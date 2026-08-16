package com.felixkroemer.smort.domain.common;

import com.felixkroemer.smort.common.exception.BulkFormatCancelledException;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkFormatEngine {

  public static final int MAX_RECENT_FAILED = 2;
  public static final int MAX_ATTEMPTS = 2;

  private final BulkFormatRepository bulkFormatRepository;
  private final AsyncTaskExecutor bulkFormatTaskExecutor;

  @FunctionalInterface
  public interface ItemProcessor<T> {
    void process(T item) throws Exception;
  }

  public void dispatch(BulkFormatEntity job, Runnable task) {
    bulkFormatTaskExecutor.execute(
        () -> {
          try {
            task.run();
          } catch (BulkFormatCancelledException e) {
            log.info("Bulk format cancelled. pk={}", job.getPk());
          } catch (Exception e) {
            log.error(
                "Unexpected error during bulk format processing. pk={}",
                job.getPk(),
                e);
          }
        });
  }

  public void cancel(BulkFormatEntity job) {
    if (job.getStatus() == BulkFormatStatus.PENDING
        || job.getStatus() == BulkFormatStatus.IN_PROGRESS
        || job.getStatus() == BulkFormatStatus.WAITING_RETRY) {
      job.setStatus(BulkFormatStatus.CANCELLED);
      bulkFormatRepository.save(job);
    }
  }

  public <T> void process(
      BulkFormatEntity job, List<T> items, ItemProcessor<T> itemProcessor) {
    int processed = 0;
    int failed = 0;
    int consecutiveFailed = 0;
    int attempts = job.getAttempts() + 1;

    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setAttempts(attempts);
    bulkFormatRepository.save(job);

    for (var item : items) {
      try {
        itemProcessor.process(item);
        processed++;
        consecutiveFailed = 0;
        job.setCompletedNotes(job.getCompletedNotes() + 1);
      } catch (Exception e) {
        failed++;
        consecutiveFailed++;
        log.warn(
            "Failed to format item during bulk format. pk={}", job.getPk(), e);
        if (consecutiveFailed >= MAX_RECENT_FAILED) {
          log.warn(
              "Hit consecutive failed limit while processing bulk format. pk={}",
              job.getPk());
          break;
        }
        continue;
      }

      job.setLastUpdatedAt(Instant.now());
      bulkFormatRepository.save(job);
    }

    handleProcessNotesResult(job, processed, failed);
  }

  private void handleProcessNotesResult(BulkFormatEntity job, int processed, int failed) {
    var pk = job.getPk();
    if (failed == 0) {
      job.setStatus(BulkFormatStatus.COMPLETED);
      job.setLastUpdatedAt(Instant.now());
      bulkFormatRepository.save(job);

      log.info(
          "Bulk format complete. pk={}, processed={}, failed={}",
          pk,
          processed,
          failed);
    } else {
      if (job.getAttempts() >= MAX_ATTEMPTS) {
        job.setStatus(BulkFormatStatus.FAILED);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);

        log.warn(
            "Bulk format reached max attempts. Setting to FAILED. pk={}, processed={}, failed={}",
            pk,
            processed,
            failed);
      } else {
        job.setStatus(BulkFormatStatus.WAITING_RETRY);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);
        log.info(
            "Bulk format had errors. Will resume later. pk={}, processed={}, failed={}, attempts={}",
            pk,
            processed,
            failed,
            job.getAttempts());
      }
    }
  }
}

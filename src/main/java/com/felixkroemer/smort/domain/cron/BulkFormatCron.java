package com.felixkroemer.smort.domain.cron;

import com.felixkroemer.smort.domain.anki.BulkFormatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkFormatCron {

  private static final Duration CRASH_TIMEOUT = Duration.ofMinutes(2);

  private final BulkFormatRepository bulkFormatRepository;
  private final BulkFormatService bulkFormatService;

  @Scheduled(fixedDelayString = "${app.scheduling.bulk-format-delay}")
  public void resumeCrashedBulkFormats() {
    var allJobs = bulkFormatRepository.findAllActive();
    for (var job : allJobs) {
      if (Duration.between(job.getLastUpdatedAt(), Instant.now()).compareTo(CRASH_TIMEOUT) > 0) {
        log.warn(
            "Resuming IN_PROGRESS bulk format. analysisId={}, lastUpdate={}, attempts={}",
            job.getAnalysisId(),
            job.getLastUpdatedAt(),
            job.getAttempts());
        try {
          bulkFormatService.resumeBulkFormat(job.getAnalysisId());
        } catch (Exception e) {
          log.error("Failed to resume bulk format. analysisId={}", job.getAnalysisId(), e);
        }
      }
    }
  }
}

package com.felixkroemer.smort.domain.cron;

import com.felixkroemer.smort.domain.anki.AnalysisBulkFormatService;
import com.felixkroemer.smort.domain.deck.DeckBulkFormatService;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
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
  private final AnalysisBulkFormatService analysisBulkFormatService;
  private final DeckBulkFormatService deckBulkFormatService;

  @Scheduled(fixedDelayString = "${app.scheduling.bulk-format-delay}")
  public void resumeCrashedBulkFormats() {
    var allJobs = bulkFormatRepository.findAllActive();
    for (var job : allJobs) {
      if (Duration.between(job.getLastUpdatedAt(), Instant.now()).compareTo(CRASH_TIMEOUT) > 0) {
        log.warn(
            "Resuming IN_PROGRESS bulk format. pk={}, lastUpdate={}, attempts={}",
            job.getPk(),
            job.getLastUpdatedAt(),
            job.getAttempts());
        try {
          resume(job);
        } catch (Exception e) {
          log.error("Failed to resume bulk format. pk={}", job.getPk(), e);
        }
      }
    }
  }

  private void resume(BulkFormatEntity job) {
    if (BulkFormatKeys.analysisBulkFormatSk().equals(job.getSk())) {
      analysisBulkFormatService.resumeBulkFormat((AnalysisBulkFormatEntity) job);
    } else if (BulkFormatKeys.deckBulkFormatSk().equals(job.getSk())) {
      deckBulkFormatService.resumeBulkFormat((DeckBulkFormatEntity) job);
    } else {
      throw new IllegalArgumentException(
          "Unknown bulk format job sort key: " + job.getSk());
    }
  }
}

package com.felixkroemer.smort.domain.cron;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.felixkroemer.smort.domain.anki.BulkFormatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BulkFormatCronTest {

  private BulkFormatRepository bulkFormatRepository;
  private BulkFormatService bulkFormatService;
  private BulkFormatCron cron;

  @BeforeEach
  void setUp() {
    bulkFormatRepository = mock(BulkFormatRepository.class);
    bulkFormatService = mock(BulkFormatService.class);
    cron = new BulkFormatCron(bulkFormatRepository, bulkFormatService);
  }

  @Test
  void resumesActiveJobIdlePastTimeout() {
    var analysisId = UUID.randomUUID();
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.WAITING_RETRY);
    job.setLastUpdatedAt(Instant.now().minus(Duration.ofMinutes(5)));

    when(bulkFormatRepository.findAllActive()).thenReturn(List.of(job));

    cron.resumeCrashedBulkFormats();

    verify(bulkFormatService).resumeBulkFormat(analysisId);
  }

  @Test
  void skipsActiveJobUpdatedRecently() {
    var analysisId = UUID.randomUUID();
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setLastUpdatedAt(Instant.now());

    when(bulkFormatRepository.findAllActive()).thenReturn(List.of(job));

    cron.resumeCrashedBulkFormats();

    verify(bulkFormatService, never()).resumeBulkFormat(any());
  }
}

package com.felixkroemer.smort.application.cron;

import com.felixkroemer.smort.domain.cron.BulkFormatCron;
import com.felixkroemer.smort.domain.cron.CleanupCron;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("cron")
public class CronController {

  private final CleanupCron cleanupCron;
  private final BulkFormatCron bulkFormatCron;

  @PostMapping("/deleteDecksMarkedForDeletion")
  public void deleteDecksMarkedForDeletion() {
    cleanupCron.deleteDecksMarkedForDeletion();
  }

  @PostMapping("/deleteAnalysesMarkedForDeletion")
  public void deleteAnalysesMarkedForDeletion() {
    cleanupCron.deleteAnalysesMarkedForDeletion();
  }

  @PostMapping("/resumeCrashedBulkFormats")
  public void resumeCrashedBulkFormats() {
    bulkFormatCron.resumeCrashedBulkFormats();
  }
}

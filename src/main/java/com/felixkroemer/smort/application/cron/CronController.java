package com.felixkroemer.smort.application.cron;

import com.felixkroemer.smort.domain.cron.DeckCron;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("cron")
public class CronController {

  private final DeckCron deckCron;

  @PostMapping("/deleteDecksMarkedForDeletion")
  public void deleteDecksMarkedForDeletion() {
    deckCron.deleteDecksMarkedForDeletion();
  }
}

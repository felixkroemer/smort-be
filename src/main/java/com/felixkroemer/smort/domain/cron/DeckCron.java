package com.felixkroemer.smort.domain.cron;

import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckCron {

  private final DeckRepository deckRepository;

  @Scheduled(cron = "${app.scheduling.delete-marked-decks-cron}")
  public void deleteDecksMarkedForDeletion() {
    var decksMarkedForDeletion = deckRepository.scanForDecksMarkedForDeletion();
    for (var deckMeta : decksMarkedForDeletion) {
      try {
        deckRepository.deleteDeckNotes(deckMeta.getDeckId());
        deckMeta.setStatus(DeckStatus.DELETED);
        deckRepository.saveDeckMeta(deckMeta);
      } catch (Exception e) {
        log.error(
            "Could not delete deck marked for deletion. deckId={}", deckMeta.getDeckId());
      }
    }
  }
}

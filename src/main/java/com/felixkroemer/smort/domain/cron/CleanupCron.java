package com.felixkroemer.smort.domain.cron;

import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisMetaRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupCron {

  private final DeckRepository deckRepository;
  private final DraftNoteRepository draftNoteRepository;
  private final BulkFormatRepository bulkFormatRepository;
  private final ChatRepository chatRepository;
  private final AnalysisMetaRepository analysisMetaRepository;
  private final DerivedNoteRepository derivedNoteRepository;

  @Scheduled(cron = "${app.scheduling.delete-marked-decks-cron}")
  public void deleteDecksMarkedForDeletion() {
    for (var deck : deckRepository.scanForDecksMarkedForDeletion()) {
      try {
        deckRepository.deleteDeckNotes(deck.getDeckId());
        draftNoteRepository.delete(deck.getDeckId());
        bulkFormatRepository.deleteDeckJob(deck.getDeckId());
        chatRepository.deleteAll(DeckKeys.deckPk(deck.getDeckId()));
        deckRepository.deleteDeckMeta(deck.getDeckId());
      } catch (Exception e) {
        log.error("Could not fully delete deck marked for deletion. deckId={}", deck.getDeckId());
      }
    }
  }

  @Scheduled(cron = "${app.scheduling.delete-marked-analyses-cron}")
  public void deleteAnalysesMarkedForDeletion() {
    for (var analysis : analysisMetaRepository.scanForAnalysesMarkedForDeletion()) {
      try {
        if (analysis.getDbPath() != null) {
          Files.deleteIfExists(Path.of(analysis.getDbPath()));
        }
        derivedNoteRepository.deleteAnalysisDerivedNotes(analysis.getAnalysisId());
        bulkFormatRepository.delete(analysis.getAnalysisId());
        chatRepository.deleteAll(AnalysisKeys.analysisPk(analysis.getAnalysisId()));
        analysisMetaRepository.delete(analysis.getAnalysisId());
      } catch (Exception e) {
        log.warn("Could not fully delete analysis. analysisId={}", analysis.getAnalysisId(), e);
      }
    }
  }
}

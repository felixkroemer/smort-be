package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.LogSeverity;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.chat.ChatMessage;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.domain.chat.StoreNoteToolChatMessage;
import com.felixkroemer.smort.domain.chat.ToolCallHandler;
import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.domain.common.BulkFormatEngine;
import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckBulkFormatService {

  private final BulkFormatRepository bulkFormatRepository;
  private final DeckRepository deckRepository;
  private final ChatOrchestrationService chatOrchestrationService;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;
  private final BulkFormatEngine bulkFormatEngine;
  private final DeckService deckService;

  public void startBulkFormat(UUID deckId, boolean reformatAlreadyFormatted) {
    var existing = bulkFormatRepository.findBulkFormatByDeckId(deckId);
    if (existing.isPresent()) {
      var job = existing.get();
      if (job.getStatus() == BulkFormatStatus.PENDING
          || job.getStatus() == BulkFormatStatus.IN_PROGRESS
          || job.getStatus() == BulkFormatStatus.WAITING_RETRY) {
        throw new SmortException("Bulk format already in progress for deck. deckId={}", deckId);
      }
    }

    var notes = deckRepository.findNotesByDeckId(deckId);
    var job = new DeckBulkFormatEntity(deckId, reformatAlreadyFormatted);
    var notesToProcess = getNotesToProcess(notes, job);

    if (notesToProcess.isEmpty()) {
      throw new SmortException(
          HttpStatus.BAD_REQUEST,
          LogSeverity.INFO,
          "No notes to format for deck. deckId={}",
          deckId);
    }

    job.setTotalNotes(notesToProcess.size());
    bulkFormatRepository.save(job);
    bulkFormatEngine.dispatch(job, () -> processNotes(job, notesToProcess));
  }

  public void resumeBulkFormat(DeckBulkFormatEntity bulkFormatEntity) {
    bulkFormatEngine.dispatch(bulkFormatEntity, () -> processNotes(bulkFormatEntity));
  }

  public void cancelBulkFormat(UUID deckId) {
    var job =
        bulkFormatRepository
            .findBulkFormatByDeckId(deckId)
            .orElseThrow(
                () -> new NotFoundException("No bulk format job found. deckId={}", deckId));
    bulkFormatEngine.cancel(job);
  }

  private void processNotes(DeckBulkFormatEntity job) {
    var notes = deckRepository.findNotesByDeckId(job.getDeckId());
    var notesToProcess = getNotesToProcess(notes, job);
    processNotes(job, notesToProcess);
  }

  private void processNotes(DeckBulkFormatEntity job, List<NoteEntity> notesToProcess) {
    var formatInstructions = deckService.getDeckSettings(job.getDeckId()).formatInstructions();
    bulkFormatEngine.process(
        job,
        notesToProcess,
        note -> {
          Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
              Map.of(
                  StoreNoteToolChatMessage.class,
                  (tx, toolCall) -> {
                    var m = (StoreNoteToolChatMessage) toolCall;
                    note.setFront(m.front());
                    note.setBack(m.back());
                    note.setLastFormattedAt(Optional.of(Instant.now()));
                    deckRepository.saveNoteInTx(tx, note);
                  });
          chatOrchestrationService.formatNote(
              DeckKeys.deckPk(job.getDeckId()),
              note.getId(),
              note.getFront(),
              note.getBack(),
              formatInstructions,
              toolHandlers);
        });
  }

  private List<NoteEntity> getNotesToProcess(List<NoteEntity> notes, BulkFormatEntity job) {
    return notes.stream()
        .filter(
            note -> {
              var lastFormattedAt = note.getLastFormattedAt();
              if (lastFormattedAt.isEmpty()) {
                return true;
              }
              if (!job.isReformatAlreadyFormatted()) {
                return false;
              }
              return lastFormattedAt.get().isBefore(job.getCreatedAt());
            })
        .toList();
  }

  public BulkFormat getJobStatus(UUID deckId) {
    return bulkFormatRepository
        .findBulkFormatByDeckId(deckId)
        .map(bulkFormatEntityMapper::toBulkFormat)
        .orElseThrow(() -> new NotFoundException("No bulk format job found. deckId={}", deckId));
  }
}

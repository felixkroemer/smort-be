package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageResponseEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteService {

  private final DeckRepository deckRepository;
  private final ChatService chatService;
  private final ChatOrchestrationService chatOrchestrationService;

  public Optional<NoteEntity> getNote(UUID deckId, UUID noteId) {
    return deckRepository.findNoteByDeckIdAndNoteId(deckId, noteId);
  }

  public List<NoteEntity> getNotes(UUID deckId) {
    return deckRepository.findNotesByDeckId(deckId);
  }

  public NoteEntity formatNote(UUID deckId, UUID noteId) {
    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new SmortException("Note not found. id={}", noteId));

    var noteSchema = chatService.formatNote(note.getFront(), note.getBack());

    note.setFront(noteSchema.getFront());
    note.setBack(noteSchema.getBack());

    log.info("Formatted note. deckId={}, noteId={}", deckId, noteId);

    return note;
  }

  public List<ChatMessageResponseEntity> chat(UUID deckId, UUID noteId, String message) {
    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new SmortException("Note not found. id={}", noteId));

    return chatOrchestrationService.chat(
        Map.of("front", note.getFront(), "back", note.getBack()),
        DeckKeys.deckPk(deckId),
        noteId,
        message,
        (tx, front, back) -> {
          deckRepository.saveNoteInTx(tx, new NoteEntity(deckId, noteId, front, back));
        });
  }
}

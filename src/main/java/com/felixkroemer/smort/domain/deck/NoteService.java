package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.domain.deck.mapping.NoteEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  private final NoteEntityMapper noteEntityMapper;

  public Optional<NoteEntity> getNote(UUID deckId, UUID noteId) {
    return deckRepository.findNoteByDeckIdAndNoteId(deckId, noteId);
  }

  public NoteEntity formatNote(UUID deckId, UUID noteId) {
    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new NotFoundException("Note not found. id={}", noteId));

    var noteSchema = chatService.formatNote(note.getFront(), note.getBack(), Optional.empty());

    note.setFront(noteSchema.front());
    note.setBack(noteSchema.back());

    log.info("Formatted note. deckId={}, noteId={}", deckId, noteId);

    return note;
  }

  public List<ChatMessageEntity> chat(UUID deckId, UUID noteId, String message) {
    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new NotFoundException("Note not found. id={}", noteId));

    return chatOrchestrationService.chat(
        note.getContent(),
        DeckKeys.deckPk(deckId),
        noteId,
        message,
        (tx, front, back) -> {
          deckRepository.saveNoteInTx(
              tx, noteEntityMapper.toNoteEntity(deckId, noteId, new NoteSchema(front, back)));
        });
  }
}

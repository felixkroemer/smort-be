package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.domain.chat.NoteChatContext;
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
  private final ChatOrchestrationService chatOrchestrationService;
  private final NoteEntityMapper noteEntityMapper;

  public Optional<NoteEntity> getNote(UUID deckId, UUID noteId) {
    return deckRepository.findNoteByDeckIdAndNoteId(deckId, noteId);
  }

  public List<ChatMessageEntity> formatNote(UUID deckId, UUID noteId) {
    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new NotFoundException("Note not found. id={}", noteId));

    var chatMessages =
        chatOrchestrationService.formatNote(
            DeckKeys.deckPk(deckId),
            noteId,
            note.getFront(),
            note.getBack(),
            Optional.empty(),
            (tx, front, back) -> {
              note.setFront(front);
              note.setBack(back);
              deckRepository.saveNoteInTx(tx, note);
            });

    log.info("Formatted note. deckId={}, noteId={}", deckId, noteId);

    return chatMessages;
  }

  public List<ChatMessageEntity> chat(UUID deckId, UUID noteId, String message) {
    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new NotFoundException("Note not found. id={}", noteId));

    var ctx = new NoteChatContext<>(noteId, note.getContent());
    return chatOrchestrationService.noteChat(
        DeckKeys.deckPk(deckId),
        ctx,
        message,
        (tx, front, back) -> {
          deckRepository.saveNoteInTx(
              tx, noteEntityMapper.toNoteEntity(deckId, noteId, new NoteSchema(front, back)));
        });
  }
}

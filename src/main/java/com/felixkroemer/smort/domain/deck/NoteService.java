package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.chat.*;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.domain.deck.mapping.NoteEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import java.util.List;
import java.util.Map;
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
  private final ChatRepository chatRepository;
  private final NoteEntityMapper noteEntityMapper;
  private final DeckService deckService;

  public Optional<NoteEntity> getNote(UUID deckId, UUID noteId) {
    return deckRepository.findNoteByDeckIdAndNoteId(deckId, noteId);
  }

  public List<ChatMessageEntity> formatNote(UUID deckId, UUID noteId) {
    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new NotFoundException("Note not found. id={}", noteId));

    Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatMessage.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatMessage) toolCall;
              note.setFront(m.front());
              note.setBack(m.back());
              deckRepository.saveNoteInTx(tx, note);
            });

    var formatInstructions = deckService.getDeckSettings(deckId).formatInstructions();
    var chatMessages =
        chatOrchestrationService.formatNote(
            DeckKeys.deckPk(deckId),
            noteId,
            note.getFront(),
            note.getBack(),
            formatInstructions,
            toolHandlers);

    log.info("Formatted note. deckId={}, noteId={}", deckId, noteId);

    return chatMessages;
  }

  public List<ChatMessageEntity> chat(UUID deckId, UUID noteId, String message) {
    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new NotFoundException("Note not found. id={}", noteId));

    var formatInstructions = deckService.getDeckSettings(deckId).formatInstructions();

    var ctx = new NoteChatContext<>(noteId, note.getContent());

    Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatMessage.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatMessage) toolCall;
              deckRepository.saveNoteInTx(
                  tx,
                  noteEntityMapper.toNoteEntity(
                      deckId, noteId, new NoteSchema(m.front(), m.back()), "default"));
            });

    return chatOrchestrationService.noteChat(
        DeckKeys.deckPk(deckId), ctx, message, formatInstructions, toolHandlers);
  }

  public void clearChat(UUID deckId, UUID noteId) {
    chatRepository.deleteChat(DeckKeys.deckPk(deckId), noteId);
  }
}

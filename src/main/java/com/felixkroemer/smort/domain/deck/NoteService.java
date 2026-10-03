package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.chat.ChatResponse;
import com.felixkroemer.smort.domain.chat.NoteChatContext;
import com.felixkroemer.smort.domain.chat.StoreNoteToolChatResponse;
import com.felixkroemer.smort.domain.chat.ToolCallHandler;
import com.felixkroemer.smort.domain.chat.orchestration.ChatOrchestrationService;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.domain.deck.mapping.NoteEntityMapper;
import com.felixkroemer.smort.domain.user.FormattingSettingsResolver;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteService {

  private final DeckRepository deckRepository;
  private final ChatOrchestrationService chatOrchestrationService;
  private final ChatRepository chatRepository;
  private final NoteEntityMapper noteEntityMapper;
  private final DeckService deckService;
  private final FormattingSettingsResolver formattingSettingsResolver;
  private final DynamoDbEnhancedClient enhancedClient;

  public NoteEntity getNote(UUID deckId, UUID noteId) {
    return deckRepository
        .findNoteByDeckIdAndNoteId(deckId, noteId)
        .orElseThrow(() -> new NotFoundException("Could not find note. id={}", noteId));
  }

  public List<NoteEntity> getNotes(UUID deckId) {
    return deckRepository.findNotesByDeckId(deckId);
  }

  public List<NoteEntity> getAllNotes() {
    var deckIds = deckService.getDecks().stream().map(Deck::getDeckId).collect(Collectors.toSet());
    return deckRepository.findNotesByUserId("default").stream()
        .filter(note -> deckIds.contains(note.getDeckId()))
        .toList();
  }

  public void deleteNotes(UUID deckId, List<UUID> noteIds) {
    noteIds.forEach(noteId -> deleteNote(deckId, noteId));
  }

  public void deleteNote(UUID deckId, UUID noteId) {
    deckRepository.deleteNoteByDeckIdAndNoteId(deckId, noteId);
    chatRepository.deleteChat(DeckKeys.deckPk(deckId), noteId);
  }

  public void moveNotes(UUID sourceDeckId, List<UUID> noteIds, UUID targetDeckId) {
    if (noteIds == null) {
      throw new SmortException("noteIds must not be null");
    }
    if (sourceDeckId.equals(targetDeckId)) {
      throw new SmortException(
          "Cannot move notes within the same deck. deckId={}", sourceDeckId);
    }
    var uniqueNoteIds = noteIds.stream().distinct().toList();
    if (uniqueNoteIds.size() > 50) {
      throw new SmortException(
          "Cannot move more than 50 notes in one request. count={}", uniqueNoteIds.size());
    }
    if (uniqueNoteIds.isEmpty()) {
      return;
    }
    deckService.getMeta(sourceDeckId);
    deckService.getMeta(targetDeckId);
    var notesByNoteId =
        deckRepository.findNotesByDeckId(sourceDeckId).stream()
            .collect(Collectors.toMap(NoteEntity::getId, Function.identity()));

    var notes =
        uniqueNoteIds.stream()
            .map(
                noteId ->
                    Optional.ofNullable(notesByNoteId.get(noteId))
                        .orElseThrow(
                            () ->
                                new NotFoundException(
                                    "Could not find note in deck. deckId={}, noteId={}",
                                    sourceDeckId,
                                    noteId)))
            .toList();

    var txBuilder = TransactWriteItemsEnhancedRequest.builder();
    notes.forEach(
        note -> {
          deckRepository.deleteNoteInTx(txBuilder, sourceDeckId, note.getId());
          note.setPk(DeckKeys.deckPk(targetDeckId));
          note.setDeckId(targetDeckId);
          deckRepository.saveNoteInTx(txBuilder, note);
        });
    enhancedClient.transactWriteItems(txBuilder.build());

    notes.forEach(
        note -> chatRepository.deleteChat(DeckKeys.deckPk(sourceDeckId), note.getId()));

    log.info(
        "Moved notes. sourceDeckId={}, targetDeckId={}, noteIds={}",
        sourceDeckId,
        targetDeckId,
        uniqueNoteIds);
  }

  public List<ChatMessageEntity> formatNote(UUID deckId, UUID noteId) {
    var note = getNote(deckId, noteId);

    Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatResponse.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatResponse) toolCall;
              note.setFront(m.front());
              note.setBack(m.back());
              note.setLastFormattedAt(Optional.of(Instant.now()));
              deckRepository.saveNoteInTx(tx, note);
            });

    String formatInstructions =
        formattingSettingsResolver.resolve(deckService.getDeckSettings(deckId));
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

  public NoteEntity updateNote(UUID deckId, UUID noteId, String front, String back) {
    var note = getNote(deckId, noteId);

    Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatResponse.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatResponse) toolCall;
              note.setFront(m.front());
              note.setBack(m.back());
              deckRepository.saveNoteInTx(tx, note);
            });

    chatOrchestrationService.storeNote(
        DeckKeys.deckPk(deckId), noteId, front, back, toolHandlers);

    return note;
  }

  public List<ChatMessageEntity> chat(UUID deckId, UUID noteId, String message) {
    var note = getNote(deckId, noteId);

    String formatInstructions =
        formattingSettingsResolver.resolve(deckService.getDeckSettings(deckId));

    var ctx = new NoteChatContext<>(noteId, note.getContent());

    Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatResponse.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatResponse) toolCall;
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

package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.chat.ChatMessage;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.domain.chat.DeckChatContext;
import com.felixkroemer.smort.domain.chat.DeckChatToolType;
import com.felixkroemer.smort.domain.chat.DraftNoteToolChatMessage;
import com.felixkroemer.smort.domain.chat.ToolCallHandler;
import com.felixkroemer.smort.domain.common.FormattingMode;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.domain.deck.mapping.DeckEntityMapper;
import com.felixkroemer.smort.domain.deck.mapping.DraftNoteEntityMapper;
import com.felixkroemer.smort.domain.deck.mapping.NoteEntityMapper;
import com.felixkroemer.smort.domain.user.FormattingSettingsResolver;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckService {

  private final ChatOrchestrationService chatOrchestrationService;
  private final ChatRepository chatRepository;
  private final DeckRepository deckRepository;
  private final DraftNoteRepository draftNoteRepository;
  private final NoteEntityMapper noteEntityMapper;
  private final BulkFormatRepository bulkFormatRepository;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;
  private final DeckEntityMapper deckEntityMapper;
  private final DraftNoteEntityMapper draftNoteEntityMapper;
  private final DynamoDbEnhancedClient enhancedClient;
  private final FormattingSettingsResolver formattingSettingsResolver;

  public List<Deck> getDecks() {
    var deckMetas = deckRepository.findDeckMetasByUserId("default");
    return deckMetas.stream()
        .map(
            entity -> {
              var bulkFormat =
                  bulkFormatRepository
                      .findBulkFormatByDeckId(entity.getDeckId())
                      .map(bulkFormatEntityMapper::toBulkFormat);
              var draftNote =
                  draftNoteRepository
                      .findDraftNote(entity.getDeckId())
                      .map(draftNoteEntityMapper::toDraftNote);
              return deckEntityMapper.toDeck(entity, bulkFormat, draftNote);
            })
        .toList();
  }

  public void deleteDeck(UUID deckId) {
    var deck = getMeta(deckId);
    deck.setStatus(DeckStatus.MARKED_FOR_DELETION);
    deckRepository.saveDeckMeta(deck);
  }

  public DeckSettings getDeckSettings(UUID deckId) {
    return deckEntityMapper.toDeckSettings(getMeta(deckId));
  }

  public DeckSettings updateDeckSettings(
      UUID deckId,
      FormattingMode formattingMode,
      String templateId,
      String formatInstructions) {
    var deck = getMeta(deckId);
    if (formattingMode != null) deck.setFormattingMode(formattingMode);
    if (templateId != null) deck.setTemplateId(templateId);
    if (formatInstructions != null) deck.setFormatInstructions(formatInstructions);
    if (formattingMode != null || templateId != null || formatInstructions != null) {
      deckRepository.saveDeckMeta(deck);
    }
    return deckEntityMapper.toDeckSettings(deck);
  }

  public List<ChatMessageEntity> chat(UUID deckId, String message) {
    var deck = getMeta(deckId);

    String formatInstructions = formattingSettingsResolver.resolve(getDeckSettings(deckId));

    var notes =
        deckRepository.findNotesByDeckId(deckId).stream().map(NoteEntity::getFront).toList();

    var draft =
        draftNoteRepository
            .findDraftNote(deckId)
            .map(d -> new NoteSchema(d.getFront(), d.getBack()));

    var ctx = new DeckChatContext(deckId, deck.getName(), notes, draft);

    Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
        Map.of(
            DraftNoteToolChatMessage.class,
            (tx, toolCall) -> {
              var m = (DraftNoteToolChatMessage) toolCall;
              draftNoteRepository.saveInTx(tx, new DraftNoteEntity(deckId, m.front(), m.back()));
            });

    return chatOrchestrationService.deckChat(
        DeckKeys.deckPk(deckId), ctx, message, formatInstructions, toolHandlers);
  }

  public List<ChatMessageEntity> getChat(UUID deckId) {
    return chatOrchestrationService.getChat(DeckKeys.deckPk(deckId), deckId);
  }

  public void clearChat(UUID deckId) {
    chatRepository.deleteChat(DeckKeys.deckPk(deckId), deckId);
  }

  public DraftNoteEntity getDraftNote(UUID deckId) {
    return draftNoteRepository
        .findDraftNote(deckId)
        .orElseThrow(() -> new NotFoundException("Could not find draft note. deckId={}", deckId));
  }

  public void clearDraftNote(UUID deckId) {
    draftNoteRepository.delete(deckId);
  }

  public List<ChatMessageEntity> storeDraftNote(UUID deckId) {
    getMeta(deckId);
    var draft = getDraftNote(deckId);

    var note =
        noteEntityMapper.toNoteEntity(
            deckId, UUID.randomUUID(), new NoteSchema(draft.getFront(), draft.getBack()), "default");

    var addNoteMessageEntity =
        ChatMessageEntity.toolCall(
            DeckKeys.deckPk(deckId),
            deckId,
            Optional.empty(),
            UUID.randomUUID().toString(),
            Optional.empty(),
            UUID.randomUUID().toString(),
            DeckChatToolType.ADD_NOTE.name(),
            Optional.empty(),
            true,
            Map.of("front", draft.getFront(), "back", draft.getBack()));

    var txBuilder = TransactWriteItemsEnhancedRequest.builder();
    deckRepository.saveNoteInTx(txBuilder, note);
    draftNoteRepository.deleteInTxIfPresent(txBuilder, deckId);
    chatRepository.saveInTx(txBuilder, addNoteMessageEntity);
    enhancedClient.transactWriteItems(txBuilder.build());

    return List.of(addNoteMessageEntity);
  }

  private DeckMetaEntity getMeta(UUID deckId) {
    return deckRepository
        .findDeckMetaByDeckId(deckId)
        .orElseThrow(() -> new NotFoundException("Could not find deck by id. deckId={}", deckId));
  }
}

package com.felixkroemer.smort.domain.chat.orchestration;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.chat.ChatMessageMeta;
import com.felixkroemer.smort.domain.chat.ChatResponse;
import com.felixkroemer.smort.domain.chat.DeckChatContext;
import com.felixkroemer.smort.domain.chat.DeckChatToolType;
import com.felixkroemer.smort.domain.chat.DraftNoteToolChatResponse;
import com.felixkroemer.smort.domain.chat.NoteChatContext;
import com.felixkroemer.smort.domain.chat.NoteChatToolType;
import com.felixkroemer.smort.domain.chat.StoreNoteToolChatResponse;
import com.felixkroemer.smort.domain.chat.TextChatResponse;
import com.felixkroemer.smort.domain.chat.ToolCallHandler;
import com.felixkroemer.smort.domain.chat.llm.DeckChatService;
import com.felixkroemer.smort.domain.chat.llm.NoteChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@Service
@RequiredArgsConstructor
public class ChatOrchestrationService {

  private final NoteChatService noteChatService;
  private final DeckChatService deckChatService;
  private final ChatRepository chatRepository;
  private final DynamoDbEnhancedClient enhancedClient;
  private final UserActionContextService userActionContextService;

  public <T> List<ChatMessageEntity> getChat(String pk, T entityId) {
    return chatRepository.findAll(pk, entityId);
  }

  public <T> List<ChatMessageEntity> formatNote(
      String pk,
      T entityId,
      String front,
      String back,
      String formatInstructions,
      Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers) {
    return formatNote(
        pk, entityId, Map.of("front", front, "back", back), formatInstructions, toolHandlers);
  }

  public <T> List<ChatMessageEntity> formatNote(
      String pk,
      T entityId,
      Map<String, String> content,
      String formatInstructions,
      Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers) {
    var storeNoteToolChatMessage = noteChatService.formatNote(content, formatInstructions);

    var formatChatMessageEntity =
        ChatMessageEntity.toolCall(
            pk,
            entityId,
            Optional.empty(),
            storeNoteToolChatMessage.meta().responseId(),
            Optional.empty(),
            storeNoteToolChatMessage.callId(),
            NoteChatToolType.STORE_NOTE.name(),
            Optional.empty(),
            true,
            Map.of(
                "front",
                storeNoteToolChatMessage.front(),
                "back",
                storeNoteToolChatMessage.back()));

    var txBuilder = TransactWriteItemsEnhancedRequest.builder();
    chatRepository.saveInTx(txBuilder, formatChatMessageEntity);
    applyToolEffect(txBuilder, storeNoteToolChatMessage, toolHandlers);
    enhancedClient.transactWriteItems(txBuilder.build());

    return List.of(formatChatMessageEntity);
  }

  public <T> List<ChatMessageEntity> storeNote(
      String pk,
      T entityId,
      String front,
      String back,
      Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers) {
    var meta = new ChatMessageMeta(UUID.randomUUID().toString(), Optional.empty(), Instant.now());
    var storeNoteToolChatMessage = new StoreNoteToolChatResponse("", front, back, meta);

    var arguments = new HashMap<String, String>();
    arguments.put("front", front);
    arguments.put("back", back);

    var storeNoteChatMessageEntity =
        ChatMessageEntity.toolCall(
            pk,
            entityId,
            Optional.empty(),
            meta.responseId(),
            Optional.empty(),
            storeNoteToolChatMessage.callId(),
            NoteChatToolType.STORE_NOTE.name(),
            Optional.empty(),
            true,
            arguments);

    var txBuilder = TransactWriteItemsEnhancedRequest.builder();
    chatRepository.saveInTx(txBuilder, storeNoteChatMessageEntity);
    applyToolEffect(txBuilder, storeNoteToolChatMessage, toolHandlers);
    enhancedClient.transactWriteItems(txBuilder.build());

    return List.of(storeNoteChatMessageEntity);
  }

  public List<ChatMessageEntity> noteChat(
      String pk,
      NoteChatContext<?> ctx,
      String message,
      String formatInstructions,
      Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers) {
    var latestChatMessageResponseId =
        chatRepository
            .findLatestChatMessage(pk, ctx.noteId())
            .map(AbstractChatMessageEntity::getResponseId);

    var userActionContext = userActionContextService.buildContext(pk, ctx.noteId());
    var chatMessage =
        noteChatService.chat(
            ctx, message, formatInstructions, latestChatMessageResponseId, userActionContext);

    return switch (chatMessage) {
      case TextChatResponse r ->
          handleChatMessageTextResponse(pk, ctx.noteId(), message, r, latestChatMessageResponseId);
      case StoreNoteToolChatResponse r ->
          handleStoreNoteToolResponse(
              pk, ctx.noteId(), message, r, latestChatMessageResponseId, toolHandlers);
      default -> throw new SmortException("Unexpected message type received");
    };
  }

  public List<ChatMessageEntity> deckChat(
      String pk,
      DeckChatContext ctx,
      String message,
      String formatInstructions,
      Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers) {
    var latestChatMessageResponseId =
        chatRepository
            .findLatestChatMessage(pk, ctx.deckId())
            .map(AbstractChatMessageEntity::getResponseId);

    var userActionContext = userActionContextService.buildContext(pk, ctx.deckId());
    var chatMessage =
        deckChatService.chat(
            ctx, message, formatInstructions, latestChatMessageResponseId, userActionContext);

    return switch (chatMessage) {
      case TextChatResponse r ->
          handleChatMessageTextResponse(pk, ctx.deckId(), message, r, latestChatMessageResponseId);
      case DraftNoteToolChatResponse r ->
          handleDraftNoteToolResponse(
              pk, ctx.deckId(), r, latestChatMessageResponseId, toolHandlers);
      default -> throw new SmortException("Unexpected message type received");
    };
  }

  private @NonNull <T> List<ChatMessageEntity> handleStoreNoteToolResponse(
      String pk,
      T entityId,
      String message,
      StoreNoteToolChatResponse storeNoteToolChatMessageResponse,
      Optional<String> latestChatMessageResponseId,
      Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers) {
    var ackResponse =
        noteChatService.acknowledgeStoreNoteToolCall(
            storeNoteToolChatMessageResponse.callId(),
            storeNoteToolChatMessageResponse.meta().responseId());
    if (ackResponse instanceof TextChatResponse(String response, ChatMessageMeta meta)) {

      // (message -> tool call response) -> (ackMessage -> ackMessage response)

      var txBuilder = TransactWriteItemsEnhancedRequest.builder();
      var toolCallChatMessageEntity =
          ChatMessageEntity.toolCall(
              pk,
              entityId,
              Optional.of(message),
              storeNoteToolChatMessageResponse.meta().responseId(),
              latestChatMessageResponseId,
              storeNoteToolChatMessageResponse.callId(),
              NoteChatToolType.STORE_NOTE.name(),
              Optional.empty(),
              false,
              Map.of(
                  "front",
                  storeNoteToolChatMessageResponse.front(),
                  "back",
                  storeNoteToolChatMessageResponse.back()));
      chatRepository.saveInTx(txBuilder, toolCallChatMessageEntity);

      var chatMessageEntity =
          ChatMessageEntity.text(
              pk,
              entityId,
              Optional.empty(), // could include ackResponse message here, but not necessary
              meta.responseId(),
              latestChatMessageResponseId,
              response,
              Map.of());
      chatRepository.saveInTx(txBuilder, chatMessageEntity);

      applyToolEffect(txBuilder, storeNoteToolChatMessageResponse, toolHandlers);
      enhancedClient.transactWriteItems(txBuilder.build());
      return List.of(toolCallChatMessageEntity, chatMessageEntity);
    } else {
      throw new SmortException("Expected ChatMessageTextResponse in response to tool call ack.");
    }
  }

  private @NonNull <T> List<ChatMessageEntity> handleDraftNoteToolResponse(
      String pk,
      T entityId,
      DraftNoteToolChatResponse draftNoteToolChatMessageResponse,
      Optional<String> latestChatMessageResponseId,
      Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers) {
    var ackResponse =
        deckChatService.acknowledgeDraftNoteToolCall(
            draftNoteToolChatMessageResponse.callId(),
            draftNoteToolChatMessageResponse.meta().responseId());
    if (ackResponse instanceof TextChatResponse(String response, ChatMessageMeta meta)) {
      var txBuilder = TransactWriteItemsEnhancedRequest.builder();
      var toolCallChatMessageEntity =
          ChatMessageEntity.toolCall(
              pk,
              entityId,
              Optional.empty(),
              draftNoteToolChatMessageResponse.meta().responseId(),
              latestChatMessageResponseId,
              draftNoteToolChatMessageResponse.callId(),
              DeckChatToolType.DRAFT_NOTE.name(),
              Optional.empty(),
              false,
              Map.of(
                  "front",
                  draftNoteToolChatMessageResponse.front(),
                  "back",
                  draftNoteToolChatMessageResponse.back()));
      chatRepository.saveInTx(txBuilder, toolCallChatMessageEntity);

      var chatMessageEntity =
          ChatMessageEntity.text(
              pk,
              entityId,
              Optional.empty(),
              meta.responseId(),
              latestChatMessageResponseId,
              response,
              Map.of());
      chatRepository.saveInTx(txBuilder, chatMessageEntity);

      applyToolEffect(txBuilder, draftNoteToolChatMessageResponse, toolHandlers);
      enhancedClient.transactWriteItems(txBuilder.build());
      return List.of(toolCallChatMessageEntity, chatMessageEntity);
    } else {
      throw new SmortException("Expected ChatMessageTextResponse in response to tool call ack.");
    }
  }

  private void applyToolEffect(
      TransactWriteItemsEnhancedRequest.Builder tx,
      ChatResponse toolCall,
      Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers) {
    var handler = toolHandlers.get(toolCall.getClass());
    if (handler == null) {
      throw new SmortException("No tool handler registered. toolCall={}", toolCall.getClass());
    }
    handler.execute(tx, toolCall);
  }

  private @NonNull <T> List<ChatMessageEntity> handleChatMessageTextResponse(
      String pk,
      T entityId,
      String message,
      TextChatResponse r,
      Optional<String> latestChatMessageResponseId) {
    var chatMessageEntity =
        ChatMessageEntity.text(
            pk,
            entityId,
            Optional.of(message),
            r.meta().responseId(),
            latestChatMessageResponseId,
            r.response(),
            Map.of());
    chatRepository.save(chatMessageEntity);
    return List.of(chatMessageEntity);
  }
}

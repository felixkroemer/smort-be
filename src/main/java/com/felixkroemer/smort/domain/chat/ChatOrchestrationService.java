package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
      Optional<String> formatInstructions,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
    return formatNote(
        pk, entityId, Map.of("front", front, "back", back), formatInstructions, toolHandlers);
  }

  public <T> List<ChatMessageEntity> formatNote(
      String pk,
      T entityId,
      Map<String, String> content,
      Optional<String> formatInstructions,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
    var userActionContext = userActionContextService.buildContext(pk, entityId);
    var storeNoteToolChatMessage =
        noteChatService.formatNote(content, formatInstructions, userActionContext);

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
            content);

    var txBuilder = TransactWriteItemsEnhancedRequest.builder();
    chatRepository.saveInTx(txBuilder, formatChatMessageEntity);
    applyToolEffect(txBuilder, storeNoteToolChatMessage, toolHandlers);
    enhancedClient.transactWriteItems(txBuilder.build());

    return List.of(formatChatMessageEntity);
  }

  public List<ChatMessageEntity> noteChat(
      String pk,
      NoteChatContext<?> ctx,
      String message,
      Optional<String> formatInstructions,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
    var latestChatMessageResponseId =
        chatRepository
            .findLatestChatMessage(pk, ctx.noteId())
            .map(AbstractChatMessageEntity::getResponseId);

    var userActionContext = userActionContextService.buildContext(pk, ctx.noteId());

    var chatMessage =
        noteChatService.chat(
            ctx, message, formatInstructions, latestChatMessageResponseId, userActionContext);

    return switch (chatMessage) {
      case TextChatMessage r ->
          handleChatMessageTextResponse(pk, ctx.noteId(), message, r, latestChatMessageResponseId);
      case StoreNoteToolChatMessage r ->
          handleStoreNoteToolResponse(
              pk, ctx.noteId(), message, r, latestChatMessageResponseId, toolHandlers);
      default -> throw new SmortException("Unexpected message type received");
    };
  }

  public List<ChatMessageEntity> deckChat(
      String pk,
      DeckChatContext ctx,
      String message,
      Optional<String> formatInstructions,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
    var latestChatMessageResponseId =
        chatRepository
            .findLatestChatMessage(pk, ctx.deckId())
            .map(AbstractChatMessageEntity::getResponseId);

    var chatMessage =
        deckChatService.chat(ctx, message, formatInstructions, latestChatMessageResponseId);

    return switch (chatMessage) {
      case TextChatMessage r ->
          handleChatMessageTextResponse(pk, ctx.deckId(), message, r, latestChatMessageResponseId);
      case DraftNoteToolChatMessage r ->
          handleDraftNoteToolResponse(
              pk, ctx.deckId(), r, latestChatMessageResponseId, toolHandlers);
      default -> throw new SmortException("Unexpected message type received");
    };
  }

  private @NonNull <T> List<ChatMessageEntity> handleStoreNoteToolResponse(
      String pk,
      T entityId,
      String message,
      StoreNoteToolChatMessage storeNoteToolChatMessageResponse,
      Optional<String> latestChatMessageResponseId,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
    var ackResponse =
        noteChatService.acknowledgeStoreNoteToolCall(
            storeNoteToolChatMessageResponse.callId(),
            storeNoteToolChatMessageResponse.meta().responseId());
    if (ackResponse instanceof TextChatMessage(String response, ChatMessageMeta meta)) {

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
      DraftNoteToolChatMessage draftNoteToolChatMessageResponse,
      Optional<String> latestChatMessageResponseId,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
    var ackResponse =
        deckChatService.acknowledgeDraftNoteToolCall(
            draftNoteToolChatMessageResponse.callId(),
            draftNoteToolChatMessageResponse.meta().responseId());
    if (ackResponse instanceof TextChatMessage(String response, ChatMessageMeta meta)) {
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
      ChatMessage toolCall,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
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
      TextChatMessage r,
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

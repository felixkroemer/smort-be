package com.felixkroemer.smort.domain.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.TriConsumer;
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
  private final ObjectMapper mapper;

  public <T> List<ChatMessageEntity> getChat(String pk, T entityId) {
    return chatRepository.findAll(pk, entityId);
  }

  public <T> List<ChatMessageEntity> formatNote(
      String pk,
      T entityId,
      String front,
      String back,
      Optional<String> formatInstructions,
      TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler) {
    return formatNote(
        pk, entityId, Map.of("front", front, "back", back), formatInstructions, storeNoteHandler);
  }

  public <T> List<ChatMessageEntity> formatNote(
      String pk,
      T entityId,
      Map<String, String> content,
      Optional<String> formatInstructions,
      TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler) {
    var storeNoteToolChatMessage = noteChatService.formatNote(content, formatInstructions);

    String result;
    try {
      result =
          mapper.writeValueAsString(
              new NoteSchema(storeNoteToolChatMessage.front(), storeNoteToolChatMessage.back()));
    } catch (JsonProcessingException e) {
      throw new SmortException("Could not serialize formatted note", e);
    }

    var formatChatMessageEntity =
        ChatMessageEntity.toolCall(
            pk,
            entityId,
            result,
            storeNoteToolChatMessage.meta().responseId(),
            Optional.empty(),
            storeNoteToolChatMessage.callId(),
            storeNoteToolChatMessage.toolName(),
            Optional.empty(),
            true);

    enhancedClient.transactWriteItems(
        tx -> {
          chatRepository.saveInTx(tx, formatChatMessageEntity);
          storeNoteHandler.accept(
              tx, storeNoteToolChatMessage.front(), storeNoteToolChatMessage.back());
        });

    return List.of(formatChatMessageEntity);
  }

  public List<ChatMessageEntity> noteChat(
      String pk,
      NoteChatContext ctx,
      String message,
      TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler) {

    var latestChatMessage = chatRepository.findLatestChatMessage(pk, ctx.noteId());
    var latestChatMessageResponseId =
        latestChatMessage.map(AbstractChatMessageEntity::getResponseId);

    var chatMessage = noteChatService.chat(ctx, message, latestChatMessageResponseId);

    return handleChatMessageResponse(
        chatMessage, pk, ctx.noteId(), message, latestChatMessageResponseId, storeNoteHandler);
  }

  public List<ChatMessageEntity> deckChat(
      String pk,
      DeckChatContext ctx,
      String message) {

    var latestChatMessage = chatRepository.findLatestChatMessage(pk, ctx.deckId());
    var latestChatMessageResponseId =
        latestChatMessage.map(AbstractChatMessageEntity::getResponseId);

    var chatMessage = deckChatService.chat(ctx, message, latestChatMessageResponseId);

    return handleChatMessageResponse(
        chatMessage, pk, ctx.deckId(), message, latestChatMessageResponseId,
        (tx, front, back) -> {});
  }

  public <T> List<ChatMessageEntity> handleChatMessageResponse(
      ChatMessage chatMessage,
      String pk,
      T entityId,
      String message,
      Optional<String> latestChatMessageResponseId,
      TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler) {
    switch (chatMessage) {
      case TextChatMessage r -> {
        return handleChatMessageTextResponse(pk, entityId, message, r, latestChatMessageResponseId);
      }
      case StoreNoteToolChatMessage r -> {
        return handleStoreNoteToolResponse(
            pk, entityId, message, r, latestChatMessageResponseId, storeNoteHandler);
      }
    }
  }

  private @NonNull <T> List<ChatMessageEntity> handleStoreNoteToolResponse(
      String pk,
      T entityId,
      String message,
      StoreNoteToolChatMessage storeNoteToolChatMessageResponse,
      Optional<String> latestChatMessageResponseId,
      TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler) {
    var toolCallChatMessageEntity =
        ChatMessageEntity.toolCall(
            pk,
            entityId,
            message,
            storeNoteToolChatMessageResponse.meta().responseId(),
            latestChatMessageResponseId,
            storeNoteToolChatMessageResponse.callId(),
            storeNoteToolChatMessageResponse.toolName(),
            Optional.empty(),
            false);
    var ackResponse =
        noteChatService.acknowledgeStoreNoteToolCall(
            storeNoteToolChatMessageResponse.callId(),
            storeNoteToolChatMessageResponse.meta().responseId());
    if (ackResponse instanceof TextChatMessage(String text, ChatMessageMeta meta)) {
      var chatMessageEntity =
          ChatMessageEntity.text(
              pk, entityId, Optional.empty(), meta.responseId(), latestChatMessageResponseId, text);
      enhancedClient.transactWriteItems(
          tx -> {
            chatRepository.saveInTx(tx, toolCallChatMessageEntity);
            chatRepository.saveInTx(tx, chatMessageEntity);
            storeNoteHandler.accept(
                tx,
                storeNoteToolChatMessageResponse.front(),
                storeNoteToolChatMessageResponse.back());
          });
      return List.of(toolCallChatMessageEntity, chatMessageEntity);
    } else {
      throw new SmortException("Expected ChatMessageTextResponse in response to tool call ack.");
    }
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
            r.text());
    chatRepository.save(chatMessageEntity);
    return List.of(chatMessageEntity);
  }
}

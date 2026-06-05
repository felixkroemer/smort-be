package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageResponseEntity;
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

  private final ChatService chatService;
  private final ChatRepository chatRepository;
  private final DynamoDbEnhancedClient enhancedClient;

  public <T> List<ChatMessageResponseEntity> getChat(String pk, T noteId) {
    return chatRepository.findAll(pk, noteId);
  }

  public <T> List<ChatMessageResponseEntity> chat(
      Map<String, String> fields,
      String pk,
      T noteId,
      String message,
      TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler) {

    var latestChatMessage = chatRepository.findLatestChatMessage(pk, noteId);
    var latestChatMessageResponseId =
        latestChatMessage.map(AbstractChatMessageEntity::getResponseId);

    var chatMessageResponse = chatService.chat(fields, message, latestChatMessageResponseId);

    return handleChatMessageResponse(
        chatMessageResponse, pk, noteId, message, latestChatMessageResponseId, storeNoteHandler);
  }

  public <T> List<ChatMessageResponseEntity> handleChatMessageResponse(
      ChatMessageResponse chatMessageResponse,
      String pk,
      T noteId,
      String message,
      Optional<String> latestChatMessageResponseId,
      TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler) {
    switch (chatMessageResponse) {
      case ChatMessageTextResponse r -> {
        return handleChatMessageTextResponse(pk, noteId, message, r, latestChatMessageResponseId);
      }
      case StoreNoteToolResponse r -> {
        return handleStoreNoteToolResponse(
            pk, noteId, message, r, latestChatMessageResponseId, storeNoteHandler);
      }
    }
  }

  private @NonNull <T> List<ChatMessageResponseEntity> handleStoreNoteToolResponse(
      String pk,
      T noteId,
      String message,
      StoreNoteToolResponse storeNoteToolResponse,
      Optional<String> latestChatMessageResponseId,
      TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler) {
    var toolCallChatMessageEntity =
        ChatMessageResponseEntity.toolCall(
            pk,
            noteId,
            message,
            storeNoteToolResponse.meta().responseId(),
            latestChatMessageResponseId,
            storeNoteToolResponse.callId(),
            storeNoteToolResponse.toolName());
    var ackResponse =
        chatService.acknowledgeStoreNoteToolCall(
            storeNoteToolResponse.callId(), storeNoteToolResponse.meta().responseId());
    if (ackResponse instanceof ChatMessageTextResponse(String text, ChatMessageResponseMeta meta)) {
      var chatMessageEntity =
          ChatMessageResponseEntity.text(
              pk, noteId, Optional.empty(), meta.responseId(), latestChatMessageResponseId, text);
      enhancedClient.transactWriteItems(
          tx -> {
            chatRepository.saveInTx(tx, toolCallChatMessageEntity);
            chatRepository.saveInTx(tx, chatMessageEntity);
            storeNoteHandler.accept(
                tx, storeNoteToolResponse.front(), storeNoteToolResponse.back());
          });
      return List.of(toolCallChatMessageEntity, chatMessageEntity);
    } else {
      throw new SmortException("Expected ChatMessageTextResponse in response to tool call ack.");
    }
  }

  private @NonNull <T> List<ChatMessageResponseEntity> handleChatMessageTextResponse(
      String pk,
      T noteId,
      String message,
      ChatMessageTextResponse r,
      Optional<String> latestChatMessageResponseId) {
    var chatMessageEntity =
        ChatMessageResponseEntity.text(
            pk,
            noteId,
            Optional.of(message),
            r.meta().responseId(),
            latestChatMessageResponseId,
            r.text());
    chatRepository.save(chatMessageEntity);
    return List.of(chatMessageEntity);
  }
}

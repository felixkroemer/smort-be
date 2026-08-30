package com.felixkroemer.smort.infrastructure.dynamodb.chat;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.ChatKeys;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class ChatMessageEntity extends AbstractChatMessageEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private String entityId;

  public ChatMessageEntity(
      String pk,
      String entityId,
      Optional<String> message,
      String responseId,
      Optional<String> previousResponseId,
      Instant createdAt,
      ChatMessageType type,
      Optional<String> response,
      Optional<String> callId,
      Optional<String> toolName,
      boolean userInitiated) {
    super(
        type,
        response,
        callId,
        toolName,
        userInitiated,
        message,
        responseId,
        previousResponseId,
        createdAt);
    this.entityId = entityId;
    this.pk = pk;
    this.sk = ChatKeys.chatMessageSk(entityId, createdAt, responseId, userInitiated);
  }

  public static <T> ChatMessageEntity text(
      String pk,
      T entityId,
      Optional<String> message,
      String responseId,
      Optional<String> previousResponseId,
      String text) {
    return new ChatMessageEntity(
        pk,
        String.valueOf(entityId),
        message,
        responseId,
        previousResponseId,
        Instant.now(),
        ChatMessageType.TEXT,
        Optional.of(text),
        Optional.empty(),
        Optional.empty(),
        false);
  }

  public static <T> ChatMessageEntity toolCall(
      String pk,
      T entityId,
      String message,
      String responseId,
      Optional<String> previousResponseId,
      String callId,
      String toolName,
      Optional<String> response,
      boolean userInitiated) {
    return new ChatMessageEntity(
        pk,
        String.valueOf(entityId),
        Optional.of(message),
        responseId,
        previousResponseId,
        Instant.now(),
        ChatMessageType.TOOL_CALL,
        response,
        Optional.of(callId),
        Optional.of(toolName),
        userInitiated);
  }
}

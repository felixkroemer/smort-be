package com.felixkroemer.smort.infrastructure.dynamodb.chat;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.ChatKeys;
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
public class ChatMessageResponseEntity extends AbstractChatMessageEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private String noteId;

  public ChatMessageResponseEntity(
      String pk,
      String noteId,
      Optional<String> message,
      String responseId,
      Optional<String> previousResponseId,
      Instant createdAt,
      AbstractChatMessageEntityType type,
      Optional<String> response,
      Optional<String> callId,
      Optional<String> toolName) {
    super(type, response, callId, toolName, message, responseId, previousResponseId, createdAt);
    this.noteId = noteId;
    this.pk = pk;
    this.sk = ChatKeys.chatMessageSk(noteId, createdAt, responseId);
  }

  public static <T> ChatMessageResponseEntity text(
      String pk,
      T noteId,
      Optional<String> message,
      String responseId,
      Optional<String> previousResponseId,
      String text) {
    return new ChatMessageResponseEntity(
        pk,
        String.valueOf(noteId),
        message,
        responseId,
        previousResponseId,
        Instant.now(),
        AbstractChatMessageEntityType.TEXT,
        Optional.of(text),
        Optional.empty(),
        Optional.empty());
  }

  public static <T> ChatMessageResponseEntity toolCall(
      String pk,
      T noteId,
      String message,
      String responseId,
      Optional<String> previousResponseId,
      String callId,
      String toolName) {
    return new ChatMessageResponseEntity(
        pk,
        String.valueOf(noteId),
        Optional.of(message),
        responseId,
        previousResponseId,
        Instant.now(),
        AbstractChatMessageEntityType.TOOL_CALL,
        Optional.empty(),
        Optional.of(callId),
        Optional.of(toolName));
  }
}

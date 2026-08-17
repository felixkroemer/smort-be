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

  private String noteId;

  public ChatMessageEntity(
      String pk,
      String noteId,
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
    this.noteId = noteId;
    this.pk = pk;
    this.sk = ChatKeys.chatMessageSk(noteId, createdAt, responseId, userInitiated);
  }

  public static <T> ChatMessageEntity text(
      String pk,
      T noteId,
      Optional<String> message,
      String responseId,
      Optional<String> previousResponseId,
      String text) {
    return new ChatMessageEntity(
        pk,
        String.valueOf(noteId),
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
      T noteId,
      String message,
      String responseId,
      Optional<String> previousResponseId,
      String callId,
      String toolName,
      boolean userInitiated) {
    return new ChatMessageEntity(
        pk,
        String.valueOf(noteId),
        Optional.of(message),
        responseId,
        previousResponseId,
        Instant.now(),
        ChatMessageType.TOOL_CALL,
        Optional.empty(),
        Optional.of(callId),
        Optional.of(toolName),
        userInitiated);
  }

  public static <T> ChatMessageEntity format(
      String pk, T noteId, String result, String responseId, String toolName) {
    return new ChatMessageEntity(
        pk,
        String.valueOf(noteId),
        Optional.of("user-initiated format"),
        responseId,
        Optional.empty(),
        Instant.now(),
        ChatMessageType.TOOL_CALL,
        Optional.of(result),
        Optional.empty(),
        Optional.of(toolName),
        true);
  }
}

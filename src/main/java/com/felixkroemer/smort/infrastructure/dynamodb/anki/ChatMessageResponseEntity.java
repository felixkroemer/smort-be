package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntityType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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

  private UUID analysisId;
  private Long deckId;
  private Long sourceNoteId;

  public ChatMessageResponseEntity(
      UUID analysisId,
      Long deckId,
      Long sourceNoteId,
      Optional<String> message,
      String responseId,
      Optional<String> previousResponseId,
      Instant createdAt,
      AbstractChatMessageEntityType type,
      Optional<String> response,
      Optional<String> callId,
      Optional<String> toolName) {
    super(type, response, callId, toolName, message, responseId, previousResponseId, createdAt);
    this.analysisId = analysisId;
    this.deckId = deckId;
    this.sourceNoteId = sourceNoteId;
    this.pk = AnkiKeys.pk(analysisId);
    this.sk = AnkiKeys.chatMessageSk(deckId, sourceNoteId, createdAt, responseId);
  }

  public static ChatMessageResponseEntity text(
      UUID analysisId,
      Long deckId,
      Long sourceNoteId,
      Optional<String> message,
      String responseId,
      Optional<String> previousResponseId,
      String text) {
    return new ChatMessageResponseEntity(
        analysisId,
        deckId,
        sourceNoteId,
        message,
        responseId,
        previousResponseId,
        Instant.now(),
        AbstractChatMessageEntityType.TEXT,
        Optional.of(text),
        Optional.empty(),
        Optional.empty());
  }

  public static ChatMessageResponseEntity toolCall(
      UUID analysisId,
      Long deckId,
      Long sourceNoteId,
      String message,
      String responseId,
      Optional<String> previousResponseId,
      String callId,
      String toolName) {
    return new ChatMessageResponseEntity(
        analysisId,
        deckId,
        sourceNoteId,
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

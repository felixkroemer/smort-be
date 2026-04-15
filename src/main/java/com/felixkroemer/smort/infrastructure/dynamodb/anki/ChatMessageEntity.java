package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntity;
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
public class ChatMessageEntity extends AbstractChatMessageEntity {

  private String pk;
  private String sk;
  private UUID analysisId;
  private Long deckId;
  private Long sourceNoteId;

  public ChatMessageEntity(
      UUID analysisId,
      Long deckId,
      Long sourceNoteId,
      String response,
      String message,
      String responseId,
      Optional<String> previousResponseId,
      Instant createdAt) {
    super(response, message, responseId, previousResponseId, createdAt);
    this.analysisId = analysisId;
    this.deckId = deckId;
    this.sourceNoteId = sourceNoteId;
    this.pk = AnkiKeys.pk(analysisId);
    this.sk = AnkiKeys.chatMessageSk(deckId, sourceNoteId, createdAt, responseId);
  }

  @DynamoDbPartitionKey
  public String getPk() {
    return pk;
  }

  @DynamoDbSortKey
  public String getSk() {
    return sk;
  }
}

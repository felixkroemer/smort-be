package com.felixkroemer.smort.infrastructure.dynamodb.analysis;

import java.time.Instant;
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
public class ChatMessageEntity {

  private UUID analysisId;
  private Long sourceNoteId;
  private UUID uuid;
  private String role;
  private String contentJson;
  private Instant createdAt;

  public ChatMessageEntity(
      UUID analysisId,
      Long sourceNoteId,
      UUID uuid,
      String role,
      String contentJson,
      Instant createdAt) {
    this.analysisId = analysisId;
    this.sourceNoteId = sourceNoteId;
    this.uuid = uuid;
    this.role = role;
    this.contentJson = contentJson;
    this.createdAt = createdAt;
  }

  @DynamoDbPartitionKey
  public String getPk() {
    return "ANALYSIS#" + analysisId;
  }

  @DynamoDbSortKey
  public String getSk() {
    return "NOTE#" + sourceNoteId + "#MSG#" + uuid;
  }

  public static String buildSkPrefix(Long noteId) {
    return "NOTE#" + noteId + "#MSG";
  }
}

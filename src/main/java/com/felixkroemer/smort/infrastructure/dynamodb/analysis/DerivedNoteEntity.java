package com.felixkroemer.smort.infrastructure.dynamodb.analysis;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class DerivedNoteEntity {

  private String sfld;
  private UUID analysisId;
  private Long sourceNoteId;
  private Map<String, String> fields;
  private Instant createdAt;

  public DerivedNoteEntity(
          UUID analysisId,
          Long sourceNoteId,
          String sfld,
          Map<String, String> fields,
          Instant createdAt) {
    this.analysisId = analysisId;
    this.sfld = sfld;
    this.sourceNoteId = sourceNoteId;
    this.fields = fields;
    this.createdAt = createdAt;
  }

  @DynamoDbPartitionKey
  public String getPk() { return "ANALYSIS#" + analysisId; }

  @DynamoDbSortKey
  public String getSk() { return "NOTE#" + sourceNoteId + "#DERIVED#" + sfld; }
  
}
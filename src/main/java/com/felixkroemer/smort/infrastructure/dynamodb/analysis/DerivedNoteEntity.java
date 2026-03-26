package com.felixkroemer.smort.infrastructure.dynamodb.analysis;

import java.util.List;
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
public class DerivedNoteEntity {

  private String pk;
  private String sk;
  private UUID analysisId;
  private Long deckId;
  private Long sourceNoteId;
  private List<String> flds;

  public DerivedNoteEntity(UUID analysisId, Long deckId, Long sourceNoteId, List<String> flds) {
    this.analysisId = analysisId;
    this.deckId = deckId;
    this.sourceNoteId = sourceNoteId;
    this.flds = flds;
    this.pk = "ANALYSIS#" + analysisId;
    this.sk = "DECK#" + deckId + "#NOTE#" + sourceNoteId;
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

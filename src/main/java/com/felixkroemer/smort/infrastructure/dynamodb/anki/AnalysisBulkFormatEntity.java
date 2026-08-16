package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
@NoArgsConstructor
public class AnalysisBulkFormatEntity extends BulkFormatEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  @Setter
  private String pk;

  public AnalysisBulkFormatEntity(UUID analysisId, boolean reformatAlreadyFormatted) {
    this.pk = AnalysisKeys.analysisPk(analysisId);
    initialize(BulkFormatKeys.bulkFormatSk(), reformatAlreadyFormatted);
  }

  public UUID getAnalysisId() {
    return UUID.fromString(pk.substring("ANALYSIS#".length()));
  }
}

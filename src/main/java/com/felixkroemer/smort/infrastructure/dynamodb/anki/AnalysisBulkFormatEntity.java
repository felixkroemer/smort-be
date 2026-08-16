package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.UUID;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
@NoArgsConstructor
public class AnalysisBulkFormatEntity extends BulkFormatEntity {

  public AnalysisBulkFormatEntity(UUID analysisId, boolean reformatAlreadyFormatted) {
    initialize(AnalysisKeys.analysisPk(analysisId), BulkFormatKeys.bulkFormatSk(), reformatAlreadyFormatted);
  }

  public UUID getAnalysisId() {
    return UUID.fromString(pk.substring("ANALYSIS#".length()));
  }

  @Override
  public UUID getOwnerId() {
    return getAnalysisId();
  }
}

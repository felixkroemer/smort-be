package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BulkFormatRepository {

  private final DynamoDbTable<BulkFormatEntity> bulkFormatTable;

  public Optional<BulkFormatEntity> findBulkFormatByAnalysisId(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(BulkFormatKeys.bulkFormatSk())
            .build();

    return Optional.ofNullable(bulkFormatTable.getItem(key));
  }

  public void save(BulkFormatEntity bulkFormatEntity) {
    bulkFormatTable.putItem(bulkFormatEntity);
  }
}

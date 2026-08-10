package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BulkFormatRepository {

  private final DynamoDbTable<BulkFormatEntity> bulkFormatTable;
  private final DynamoDbIndex<BulkFormatEntity> statusBulkFormatIndex;

  public Optional<BulkFormatEntity> findBulkFormatByAnalysisId(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(BulkFormatKeys.bulkFormatSk())
            .build();

    return Optional.ofNullable(bulkFormatTable.getItem(key));
  }

  public List<BulkFormatEntity> findAllInProgress() {
    return statusBulkFormatIndex
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(BulkFormatStatus.IN_PROGRESS.name()).build()))
                .build())
        .stream()
        .flatMap(page -> page.items().stream())
        .toList();
  }

  public void save(BulkFormatEntity bulkFormatEntity) {
    bulkFormatTable.putItem(bulkFormatEntity);
  }

  public void delete(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(BulkFormatKeys.bulkFormatSk())
            .build();
    bulkFormatTable.deleteItem(key);
  }
}

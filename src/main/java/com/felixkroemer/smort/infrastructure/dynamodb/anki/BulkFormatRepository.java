package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.common.exception.BulkFormatCancelledException;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

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

  public List<BulkFormatEntity> findAllActive() {
    return Stream.of(BulkFormatStatus.IN_PROGRESS, BulkFormatStatus.WAITING_RETRY)
        .flatMap(
            status ->
                statusBulkFormatIndex
                    .query(
                        QueryEnhancedRequest.builder()
                            .queryConditional(
                                QueryConditional.keyEqualTo(
                                    Key.builder().partitionValue(status.name()).build()))
                            .build())
                    .stream()
                    .flatMap(page -> page.items().stream()))
        .toList();
  }

  public void save(BulkFormatEntity bulkFormatEntity) {
    try {
      bulkFormatTable.putItem(
          PutItemEnhancedRequest.builder(BulkFormatEntity.class)
              .item(bulkFormatEntity)
              .conditionExpression(
                  Expression.builder()
                      .expression("attribute_not_exists(#status) OR #status <> :cancelled")
                      .putExpressionName("#status", "status")
                      .putExpressionValue(
                          ":cancelled", AttributeValue.fromS(BulkFormatStatus.CANCELLED.name()))
                      .build())
              .build());
    } catch (ConditionalCheckFailedException e) {
      throw new BulkFormatCancelledException(
          "Bulk format was cancelled. analysisId={}", bulkFormatEntity.getAnalysisId());
    }
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

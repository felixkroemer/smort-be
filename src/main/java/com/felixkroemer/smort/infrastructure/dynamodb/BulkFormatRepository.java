package com.felixkroemer.smort.infrastructure.dynamodb;

import com.felixkroemer.smort.common.exception.BulkFormatCancelledException;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.List;
import java.util.Map;
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

  private final DynamoDbTable<AnalysisBulkFormatEntity> analysisBulkFormatTable;
  private final DynamoDbTable<DeckBulkFormatEntity> deckBulkFormatTable;
  private final DynamoDbIndex<AnalysisBulkFormatEntity> statusAnalysisBulkFormatIndex;
  private final DynamoDbIndex<DeckBulkFormatEntity> statusDeckBulkFormatIndex;

  public Optional<AnalysisBulkFormatEntity> findBulkFormatByAnalysisId(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(BulkFormatKeys.bulkFormatSk())
            .build();
    return Optional.ofNullable(analysisBulkFormatTable.getItem(key));
  }

  public Optional<DeckBulkFormatEntity> findBulkFormatByDeckId(UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(BulkFormatKeys.deckBulkFormatSk())
            .build();
    return Optional.ofNullable(deckBulkFormatTable.getItem(key));
  }

  public List<BulkFormatEntity> findAllActive() {
    return Stream.of(BulkFormatStatus.IN_PROGRESS, BulkFormatStatus.WAITING_RETRY)
        .flatMap(
            status ->
                Stream.concat(
                    queryIndex(statusAnalysisBulkFormatIndex, status, BulkFormatKeys.bulkFormatSk()),
                    queryIndex(statusDeckBulkFormatIndex, status, BulkFormatKeys.deckBulkFormatSk())))
        .toList();
  }

  private <T extends BulkFormatEntity> Stream<BulkFormatEntity> queryIndex(
      DynamoDbIndex<T> index, BulkFormatStatus status, String sk) {
    return index
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.keyEqualTo(Key.builder().partitionValue(status.name()).build()))
                .filterExpression(
                    Expression.builder()
                        .expression("#sk = :sk")
                        .expressionNames(Map.of("#sk", "sk"))
                        .expressionValues(Map.of(":sk", AttributeValue.fromS(sk)))
                        .build())
                .build())
        .stream()
        .flatMap(page -> page.items().stream());
  }

  public void save(BulkFormatEntity entity) {
    if (entity instanceof AnalysisBulkFormatEntity analysisJob) {
      save(analysisBulkFormatTable, analysisJob);
    } else if (entity instanceof DeckBulkFormatEntity deckJob) {
      save(deckBulkFormatTable, deckJob);
    } else {
      throw new IllegalArgumentException(
          "Unsupported bulk format entity type: " + entity.getClass().getName());
    }
  }

  private <T extends BulkFormatEntity> void save(DynamoDbTable<T> table, T entity) {
    try {
      table.putItem(
          PutItemEnhancedRequest.<T>builder((Class<T>) entity.getClass())
              .item(entity)
              .conditionExpression(
                  Expression.builder()
                      .expression(
                          "attribute_not_exists(#status)"
                              + " OR (:newCreatedAt = #createdAt AND #status <> :cancelled)"
                              + " OR :newCreatedAt > #createdAt")
                      .putExpressionName("#status", "status")
                      .putExpressionName("#createdAt", "createdAt")
                      .putExpressionValue(
                          ":cancelled", AttributeValue.fromS(BulkFormatStatus.CANCELLED.name()))
                      .putExpressionValue(
                          ":newCreatedAt",
                          AttributeValue.fromS(entity.getCreatedAt().toString()))
                      .build())
              .build());
    } catch (ConditionalCheckFailedException e) {
      throw new BulkFormatCancelledException(
          "Bulk format was cancelled. pk={}", entity.getPk());
    }
  }

  public void delete(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(BulkFormatKeys.bulkFormatSk())
            .build();
    analysisBulkFormatTable.deleteItem(key);
  }

  public void deleteDeckJob(UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(BulkFormatKeys.deckBulkFormatSk())
            .build();
    deckBulkFormatTable.deleteItem(key);
  }
}

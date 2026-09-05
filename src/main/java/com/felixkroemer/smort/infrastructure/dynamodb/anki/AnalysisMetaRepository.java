package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.MetaKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AnalysisMetaRepository {

  private final DynamoDbTable<AnalysisMetaEntity> analysisMetaTable;
  private final DynamoDbIndex<AnalysisMetaEntity> userAnalysisIndex;

  public Optional<AnalysisMetaEntity> findAnalysisMetaByAnalysisId(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(MetaKeys.metaSk())
            .build();

    return Optional.ofNullable(analysisMetaTable.getItem(key));
  }

  public List<AnalysisMetaEntity> findAnalysisMetasByUserId(String userId) {
    var condition =
        QueryConditional.keyEqualTo(
            Key.builder().partitionValue(AnalysisKeys.userAnalysisIndexGsiPk(userId)).build());

    Expression filter =
        Expression.builder()
            .expression("#status <> :status")
            .expressionNames(Map.of("#status", "status"))
            .expressionValues(
                Map.of(
                    ":status", AttributeValue.fromS(AnalysisStatus.MARKED_FOR_DELETION.toString())))
            .build();

    return userAnalysisIndex
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .filterExpression(filter)
                .build())
        .stream()
        .flatMap(page -> page.items().stream())
        .toList();
  }

  public List<AnalysisMetaEntity> scanForAnalysesMarkedForDeletion() {
    Expression filter =
        Expression.builder()
            .expression("#sk = :sk AND begins_with(#pk, :pkPrefix) AND #status = :status")
            .expressionNames(Map.of("#sk", "sk", "#pk", "pk", "#status", "status"))
            .expressionValues(
                Map.of(
                    ":sk", AttributeValue.fromS(MetaKeys.metaSk()),
                    ":pkPrefix", AttributeValue.fromS(AnalysisKeys.analysisPkPrefix()),
                    ":status", AttributeValue.fromS(AnalysisStatus.MARKED_FOR_DELETION.toString())))
            .build();

    return analysisMetaTable
        .scan(ScanEnhancedRequest.builder().filterExpression(filter).build())
        .items()
        .stream()
        .toList();
  }

  public void save(AnalysisMetaEntity entity) {
    analysisMetaTable.putItem(entity);
  }

  public void delete(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(MetaKeys.metaSk())
            .build();
    analysisMetaTable.deleteItem(key);
  }
}

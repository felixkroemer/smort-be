package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.NoteKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class DerivedNoteRepository {

  private final DynamoDbTable<DerivedNoteEntity> table;
  private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

  public List<DerivedNoteEntity> findDerivedNotesByAnalysisId(UUID analysisId) {
    QueryConditional condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(AnalysisKeys.analysisPk(analysisId))
                .sortValue(NoteKeys.notePrefix())
                .build());

    return table
        .query(QueryEnhancedRequest.builder().queryConditional(condition).build())
        .items()
        .stream()
        .toList();
  }

  public Optional<DerivedNoteEntity> finDerivedNotedByAnalysisIdAndNoteId(
      UUID analysisId, Long noteId) {
    Key key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(NoteKeys.noteSk(noteId))
            .build();

    return Optional.ofNullable(table.getItem(key));
  }

  public void save(DerivedNoteEntity entity) {
    table.putItem(entity);
  }

  public void saveInTx(
      TransactWriteItemsEnhancedRequest.Builder txBuilder, DerivedNoteEntity entity) {
    txBuilder.addPutItem(table, entity);
  }

  public void deleteAnalysisDerivedNotes(UUID analysisId) {
    var condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(AnalysisKeys.analysisPk(analysisId))
                .sortValue(NoteKeys.notePrefix())
                .build());

    List<DerivedNoteEntity> keys =
        table
            .query(
                QueryEnhancedRequest.builder()
                    .queryConditional(condition)
                    .attributesToProject("pk", "sk")
                    .build())
            .items()
            .stream()
            .toList();

    IntStream.range(0, (keys.size() + 24) / 25)
        .mapToObj(i -> keys.subList(i * 25, Math.min((i + 1) * 25, keys.size())))
        .forEach(
            batch -> {
              WriteBatch.Builder<DerivedNoteEntity> writeBatch =
                  WriteBatch.builder(DerivedNoteEntity.class).mappedTableResource(table);
              batch.forEach(
                  item ->
                      writeBatch.addDeleteItem(
                          Key.builder()
                              .partitionValue(item.getPk())
                              .sortValue(item.getSk())
                              .build()));
              dynamoDbEnhancedClient.batchWriteItem(
                  BatchWriteItemEnhancedRequest.builder().writeBatches(writeBatch.build()).build());
            });

    log.info("Deleted analysis derived notes. analysisId={}", analysisId);
  }
}

package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.NoteKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@Repository
@RequiredArgsConstructor
public class DerivedNoteRepository {

  private final DynamoDbTable<DerivedNoteEntity> table;

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

  public Optional<DerivedNoteEntity> finDerivedNotedByAnalysisIdAndNoteId(UUID analysisId, Long noteId) {
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
}

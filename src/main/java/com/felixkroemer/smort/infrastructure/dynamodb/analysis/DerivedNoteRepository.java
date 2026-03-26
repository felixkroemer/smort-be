package com.felixkroemer.smort.infrastructure.dynamodb.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
@RequiredArgsConstructor
public class DerivedNoteRepository {

  private final DynamoDbTable<DerivedNoteEntity> table;

  public List<DerivedNoteEntity> findAllByDeckId(UUID analysisId, Long deckId) {
    QueryConditional condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue("ANALYSIS#" + analysisId)
                .sortValue("DECK#" + deckId)
                .build());

    return table
        .query(QueryEnhancedRequest.builder().queryConditional(condition).build())
        .items()
        .stream()
        .toList();
  }

  public Optional<DerivedNoteEntity> findBySourceNoteId(
      UUID analysisId, Long deckId, Long sourceNoteId) {
    Key key =
        Key.builder()
            .partitionValue("ANALYSIS#" + analysisId)
            .sortValue("DECK#" + deckId + "NOTE#" + sourceNoteId)
            .build();

    return Optional.ofNullable(table.getItem(key));
  }

  public void save(DerivedNoteEntity entity) {
    table.putItem(entity);
  }
}

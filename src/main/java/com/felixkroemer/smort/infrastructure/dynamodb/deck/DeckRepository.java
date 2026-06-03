package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.MetaKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.NoteKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeckRepository {

  private final DynamoDbTable<NoteEntity> noteTable;
  private final DynamoDbTable<DeckMetaEntity> deckMetaTable;
  private final DynamoDbIndex<DeckMetaEntity> deckMetaUserDeckIndex;

  public void save(NoteEntity entity) {
    noteTable.putItem(entity);
  }

  public void save(DeckMetaEntity entity) {
    deckMetaTable.putItem(entity);
  }

  public List<DeckMetaEntity> findAllByUserId(String userId) {
    QueryConditional condition = QueryConditional.keyEqualTo(
            Key.builder()
                    .partitionValue(DeckKeys.userDeckIndexGsiPk(userId))
                    .build()
    );

    return deckMetaUserDeckIndex
            .query(QueryEnhancedRequest.builder().queryConditional(condition).build())
            .stream()
            .flatMap(page -> page.items().stream())
            .toList();
  }
}

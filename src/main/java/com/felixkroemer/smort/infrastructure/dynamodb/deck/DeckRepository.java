package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.NoteKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
@RequiredArgsConstructor
public class DeckRepository {

  private final DynamoDbTable<NoteEntity> noteTable;
  private final DynamoDbTable<DeckMetaEntity> deckMetaTable;
  private final DynamoDbIndex<DeckMetaEntity> deckMetaUserDeckIndex;

  public void saveNote(NoteEntity entity) {
    noteTable.putItem(entity);
  }

  public void saveDeckMeta(DeckMetaEntity entity) {
    deckMetaTable.putItem(entity);
  }

  public List<DeckMetaEntity> findDeckMetasByUserId(String userId) {
    var condition =
        QueryConditional.keyEqualTo(
            Key.builder().partitionValue(DeckKeys.userDeckIndexGsiPk(userId)).build());

    return deckMetaUserDeckIndex
        .query(QueryEnhancedRequest.builder().queryConditional(condition).build())
        .stream()
        .flatMap(page -> page.items().stream())
        .toList();
  }

  public Optional<NoteEntity> findNoteByDeckIdAndNoteId(UUID deckId, UUID noteId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(NoteKeys.noteSk(noteId))
            .build();

    return Optional.ofNullable(noteTable.getItem(key));
  }

  public List<NoteEntity> findNotesByDeckId(UUID deckId) {
    var condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(DeckKeys.deckPk(deckId))
                .sortValue(NoteKeys.notePrefix())
                .build());

    return noteTable.query(condition).items().stream().toList();
  }
}

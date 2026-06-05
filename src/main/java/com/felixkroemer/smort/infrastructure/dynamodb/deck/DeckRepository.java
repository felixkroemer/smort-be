package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.MetaKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.NoteKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@Repository
@RequiredArgsConstructor
@Slf4j
public class DeckRepository {

  private final DynamoDbTable<NoteEntity> noteTable;
  private final DynamoDbTable<DeckMetaEntity> deckMetaTable;
  private final DynamoDbIndex<DeckMetaEntity> deckMetaUserDeckIndex;
  private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

  public void saveNote(NoteEntity entity) {
    noteTable.putItem(entity);
  }

  public void saveNoteInTx(TransactWriteItemsEnhancedRequest.Builder txBuilder, NoteEntity note) {
    txBuilder.addPutItem(noteTable, note);
  }

  public void saveDeckMeta(DeckMetaEntity entity) {
    deckMetaTable.putItem(entity);
  }

  public List<DeckMetaEntity> findDeckMetasByUserId(String userId) {
    var condition =
        QueryConditional.keyEqualTo(
            Key.builder().partitionValue(DeckKeys.userDeckIndexGsiPk(userId)).build());

    Expression filter =
        Expression.builder()
            .expression("#s = :status")
            .expressionNames(Map.of("#s", "status"))
            .expressionValues(Map.of(":status", AttributeValue.fromS(DeckStatus.ACTIVE.toString())))
            .build();

    return deckMetaUserDeckIndex
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .filterExpression(filter)
                .build())
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

  public Optional<DeckMetaEntity> findDeckMetaByDeckId(UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(MetaKeys.metaPrefix())
            .build();

    return Optional.ofNullable(deckMetaTable.getItem(key));
  }

  public void deleteNoteByDeckIdAndNoteId(UUID deckId, UUID noteId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(NoteKeys.noteSk(noteId))
            .build();
    noteTable.deleteItem(key);
  }

  public List<DeckMetaEntity> scanForDecksMarkedForDeletion() {
    Expression filter =
        Expression.builder()
            .expression("#s = :status")
            .expressionNames(Map.of("#s", "status"))
            .expressionValues(
                Map.of(":status", AttributeValue.fromS(DeckStatus.MARKED_FOR_DELETION.toString())))
            .build();

    return deckMetaTable
        .scan(ScanEnhancedRequest.builder().filterExpression(filter).build())
        .items()
        .stream()
        .toList();
  }

  public void deleteDeckNotes(UUID deckId) {
    var condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(DeckKeys.deckPk(deckId))
                .sortValue(NoteKeys.notePrefix())
                .build());

    List<NoteEntity> keys =
        noteTable
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
              WriteBatch.Builder<NoteEntity> writeBatch =
                  WriteBatch.builder(NoteEntity.class).mappedTableResource(noteTable);
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

    log.info("Deleted deck. deckId={}", deckId);
  }
}

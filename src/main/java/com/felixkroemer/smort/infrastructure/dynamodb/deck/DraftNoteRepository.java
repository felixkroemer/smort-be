package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.DraftNoteKeys;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactDeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@Repository
@RequiredArgsConstructor
public class DraftNoteRepository {

  private final DynamoDbTable<DraftNoteEntity> draftNoteTable;

  public void saveInTx(
      TransactWriteItemsEnhancedRequest.Builder txBuilder, DraftNoteEntity entity) {
    txBuilder.addPutItem(draftNoteTable, entity);
  }

  public void deleteInTxIfPresent(
      TransactWriteItemsEnhancedRequest.Builder txBuilder, UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(DraftNoteKeys.draftNoteSk())
            .build();
    var condition =
        Expression.builder().expression("attribute_exists(pk) AND attribute_exists(sk)").build();
    txBuilder.addDeleteItem(
        draftNoteTable,
        TransactDeleteItemEnhancedRequest.builder()
            .key(key)
            .conditionExpression(condition)
            .build());
  }

  public Optional<DraftNoteEntity> findDraftNote(UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(DraftNoteKeys.draftNoteSk())
            .build();
    return Optional.ofNullable(draftNoteTable.getItem(key));
  }

  public void delete(UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(DraftNoteKeys.draftNoteSk())
            .build();
    draftNoteTable.deleteItem(key);
  }
}

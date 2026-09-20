package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.JottingKeys;
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
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

@Repository
@RequiredArgsConstructor
@Slf4j
public class JottingRepository {

  private final DynamoDbTable<JottingEntity> jottingTable;
  private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

  public void save(JottingEntity jotting) {
    jottingTable.putItem(jotting);
  }

  public List<JottingEntity> findJottingsByDeckId(UUID deckId) {
    var condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(DeckKeys.deckPk(deckId))
                .sortValue(JottingKeys.jottingPrefix())
                .build());

    return jottingTable.query(condition).items().stream().toList();
  }

  public Optional<JottingEntity> findJotting(UUID deckId, UUID jottingId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(JottingKeys.jottingSk(jottingId))
            .build();

    return Optional.ofNullable(jottingTable.getItem(key));
  }

  public void delete(UUID deckId, UUID jottingId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(JottingKeys.jottingSk(jottingId))
            .build();
    jottingTable.deleteItem(key);
  }

  public void deleteAllJottings(UUID deckId) {
    var condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(DeckKeys.deckPk(deckId))
                .sortValue(JottingKeys.jottingPrefix())
                .build());

    List<JottingEntity> keys =
        jottingTable
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
              WriteBatch.Builder<JottingEntity> writeBatch =
                  WriteBatch.builder(JottingEntity.class).mappedTableResource(jottingTable);
              batch.forEach(
                  item ->
                      writeBatch.addDeleteItem(
                          Key.builder()
                              .partitionValue(item.getPk())
                              .sortValue(item.getSk())
                              .build()));
              dynamoDbEnhancedClient.batchWriteItem(
                  BatchWriteItemEnhancedRequest.builder()
                      .writeBatches(writeBatch.build())
                      .build());
            });

    log.info("Deleted deck jottings. deckId={}", deckId);
  }
}

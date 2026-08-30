package com.felixkroemer.smort.infrastructure.dynamodb.chat;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.ChatKeys;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

@Repository
@RequiredArgsConstructor
public class ChatRepository {

  private final DynamoDbTable<ChatMessageEntity> table;
  private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

  public <T> Optional<ChatMessageEntity> findLatestChatMessage(String pk, T entityId) {
    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(
                QueryConditional.sortBeginsWith(
                    Key.builder()
                        .partitionValue(pk)
                        .sortValue(ChatKeys.llmChatMessagesPrefix(entityId))
                        .build()))
            .scanIndexForward(false)
            .limit(1)
            .build();

    return table.query(request).items().stream().findFirst();
  }

  public <T> List<ChatMessageEntity> findAll(String pk, T entityId) {
    return Stream.concat(
            queryByPrefix(pk, ChatKeys.llmChatMessagesPrefix(entityId)).stream(),
            queryByPrefix(pk, ChatKeys.userChatMessagesPrefix(entityId)).stream())
        .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt).reversed())
        .toList();
  }

  private List<ChatMessageEntity> queryByPrefix(String pk, String sortKeyPrefix) {
    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(
                QueryConditional.sortBeginsWith(
                    Key.builder().partitionValue(pk).sortValue(sortKeyPrefix).build()))
            .scanIndexForward(false)
            .build();

    return table.query(request).items().stream().toList();
  }

  public void save(ChatMessageEntity chatMessage) {
    table.putItem(chatMessage);
  }

  public void saveInTx(
      TransactWriteItemsEnhancedRequest.Builder txBuilder, ChatMessageEntity chatMessage) {
    txBuilder.addPutItem(table, chatMessage);
  }

  public void deleteAll(String pk) {
    deleteKeys(queryKeys(pk, "CHAT#"));
  }

  public <T> void deleteChat(String pk, T entityId) {
    deleteKeys(
        Stream.concat(
                queryKeys(pk, ChatKeys.llmChatMessagesPrefix(entityId)).stream(),
                queryKeys(pk, ChatKeys.userChatMessagesPrefix(entityId)).stream())
            .toList());
  }

  private List<ChatMessageEntity> queryKeys(String pk, String sortKeyPrefix) {
    return table
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.sortBeginsWith(
                        Key.builder().partitionValue(pk).sortValue(sortKeyPrefix).build()))
                .attributesToProject("pk", "sk")
                .build())
        .items()
        .stream()
        .toList();
  }

  private void deleteKeys(List<ChatMessageEntity> keys) {
    IntStream.range(0, (keys.size() + 24) / 25)
        .mapToObj(i -> keys.subList(i * 25, Math.min((i + 1) * 25, keys.size())))
        .forEach(
            batch -> {
              WriteBatch.Builder<ChatMessageEntity> writeBatch =
                  WriteBatch.builder(ChatMessageEntity.class).mappedTableResource(table);
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
  }
}

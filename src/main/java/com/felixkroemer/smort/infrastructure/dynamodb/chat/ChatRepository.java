package com.felixkroemer.smort.infrastructure.dynamodb.chat;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.ChatKeys;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@Repository
@RequiredArgsConstructor
public class ChatRepository {

  private final DynamoDbTable<ChatMessageEntity> table;

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
}

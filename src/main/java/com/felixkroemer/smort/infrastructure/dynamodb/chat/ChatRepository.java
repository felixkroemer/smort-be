package com.felixkroemer.smort.infrastructure.dynamodb.chat;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.ChatKeys;
import java.util.List;
import java.util.Optional;
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

  private final DynamoDbTable<ChatMessageResponseEntity> table;

  public <T> Optional<ChatMessageResponseEntity> findLatestChatMessage(String pk, T noteId) {

    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(
                QueryConditional.sortBeginsWith(
                    Key.builder()
                        .partitionValue(pk)
                        .sortValue(ChatKeys.chatMessagePrefix(noteId))
                        .build()))
            .scanIndexForward(false)
            .limit(1)
            .build();

    return table.query(request).items().stream().findFirst();
  }

  public <T> List<ChatMessageResponseEntity> findAll(String pk, T noteId) {
    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(
                QueryConditional.sortBeginsWith(
                    Key.builder()
                        .partitionValue(pk)
                        .sortValue(ChatKeys.chatMessagePrefix(noteId))
                        .build()))
            .scanIndexForward(false)
            .limit(1)
            .build();

    return table.query(request).items().stream().toList();
  }

  public void save(ChatMessageResponseEntity chatMessage) {
    table.putItem(chatMessage);
  }

  public void saveInTx(
      TransactWriteItemsEnhancedRequest.Builder txBuilder, ChatMessageResponseEntity chatMessage) {
    txBuilder.addPutItem(table, chatMessage);
  }
}

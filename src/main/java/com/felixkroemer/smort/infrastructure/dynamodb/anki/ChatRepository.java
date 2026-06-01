package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
@RequiredArgsConstructor
public class ChatRepository {

  private final DynamoDbTable<ChatMessageResponseEntity> table;
  private final DynamoDbEnhancedClient enhancedClient;

  public Optional<ChatMessageResponseEntity> findLatestChatMessage(
      UUID analysisId, Long noteId) {

    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(
                QueryConditional.sortBeginsWith(
                    Key.builder()
                        .partitionValue(AnkiKeys.pk(analysisId))
                        .sortValue(AnkiKeys.chatMessagePrefix(noteId))
                        .build()))
            .scanIndexForward(false)
            .limit(1)
            .build();

    return table.query(request).items().stream().findFirst();
  }

  public List<ChatMessageResponseEntity> findAll(UUID analysisId, Long noteId) {
    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(
                QueryConditional.sortBeginsWith(
                    Key.builder()
                        .partitionValue(AnkiKeys.pk(analysisId))
                        .sortValue(AnkiKeys.chatMessagePrefix(noteId))
                        .build()))
            .scanIndexForward(false)
            .limit(1)
            .build();

    return table.query(request).items().stream().toList();
  }

  public void save(ChatMessageResponseEntity chatMessage) {
    table.putItem(chatMessage);
  }

  public void saveInTransaction(ChatMessageResponseEntity first, ChatMessageResponseEntity second) {
    enhancedClient.transactWriteItems(tx -> tx.addPutItem(table, first).addPutItem(table, second));
  }
}

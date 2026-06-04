package com.felixkroemer.smort.infrastructure.dynamodb.chat;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.ChatKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

  public Optional<ChatMessageResponseEntity> findLatestChatMessage(UUID analysisId, Long noteId) {

    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(
                QueryConditional.sortBeginsWith(
                    Key.builder()
                        .partitionValue(AnalysisKeys.analysisPk(analysisId))
                        .sortValue(ChatKeys.chatMessagePrefix(noteId))
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
                        .partitionValue(AnalysisKeys.analysisPk(analysisId))
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

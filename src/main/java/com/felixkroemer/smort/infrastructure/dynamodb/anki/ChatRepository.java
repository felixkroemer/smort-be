package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
@RequiredArgsConstructor
public class ChatRepository {

  private final DynamoDbTable<ChatMessageEntity> table;

  public Optional<ChatMessageEntity> findLatestChatMessage(
      UUID analysisId, Long deckId, Long sourceNoteId) {

    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(
                QueryConditional.sortBeginsWith(
                    Key.builder()
                        .partitionValue(AnkiKeys.pk(analysisId))
                        .sortValue(AnkiKeys.chatMessagePrefix(deckId, sourceNoteId))
                        .build()))
            .scanIndexForward(false)
            .limit(1)
            .build();

    return table.query(request).items().stream().findFirst();
  }

  public void save(ChatMessageEntity chatMessage) {
    table.putItem(chatMessage);
  }
}

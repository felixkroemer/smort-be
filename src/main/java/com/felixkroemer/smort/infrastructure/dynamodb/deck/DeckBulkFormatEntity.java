package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.UUID;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
@NoArgsConstructor
public class DeckBulkFormatEntity extends BulkFormatEntity {

  public DeckBulkFormatEntity(UUID deckId, boolean reformatAlreadyFormatted) {
    initialize(DeckKeys.deckPk(deckId), BulkFormatKeys.deckBulkFormatSk(), reformatAlreadyFormatted);
  }

  public UUID getDeckId() {
    return UUID.fromString(pk.substring("DECK#".length()));
  }

  @Override
  public UUID getOwnerId() {
    return getDeckId();
  }
}

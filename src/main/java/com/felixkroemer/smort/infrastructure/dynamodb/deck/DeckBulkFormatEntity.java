package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
@NoArgsConstructor
public class DeckBulkFormatEntity extends BulkFormatEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  @Setter
  private String pk;

  public DeckBulkFormatEntity(UUID deckId, boolean reformatAlreadyFormatted) {
    this.pk = DeckKeys.deckPk(deckId);
    initialize(BulkFormatKeys.deckBulkFormatSk(), reformatAlreadyFormatted);
  }

  public UUID getDeckId() {
    return UUID.fromString(pk.substring("DECK#".length()));
  }
}

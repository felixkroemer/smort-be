package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.JottingKeys;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class JottingEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private UUID id;
  private UUID deckId;
  private String title;
  private String description;

  public JottingEntity(UUID deckId, UUID jottingId, String title, String description) {
    this.pk = DeckKeys.deckPk(deckId);
    this.sk = JottingKeys.jottingSk(jottingId);
    this.id = jottingId;
    this.deckId = deckId;
    this.title = title;
    this.description = description;
  }
}

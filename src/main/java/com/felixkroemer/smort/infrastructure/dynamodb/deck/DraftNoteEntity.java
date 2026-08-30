package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.DraftNoteKeys;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class DraftNoteEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private String front;
  private String back;

  public DraftNoteEntity(UUID deckId, String front, String back) {
    this.pk = DeckKeys.deckPk(deckId);
    this.sk = DraftNoteKeys.draftNoteSk();
    this.front = front;
    this.back = back;
  }
}
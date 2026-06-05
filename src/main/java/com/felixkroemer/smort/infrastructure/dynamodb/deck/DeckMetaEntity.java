package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.MetaKeys;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class DeckMetaEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  @Getter(onMethod_ = @DynamoDbSecondaryPartitionKey(indexNames = "UserDeckIndex"))
  private String userDeckIndexGsiPk;

  @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "UserDeckIndex"))
  private String userDeckIndexGsiSk;

  private UUID deckId;

  private String name;

  private String userId;
  
  private DeckStatus status;

  public DeckMetaEntity(UUID deckId, String name, String userId) {
    this.pk = DeckKeys.deckPk(deckId);
    this.sk = MetaKeys.metaSk();
    this.userDeckIndexGsiPk = DeckKeys.userDeckIndexGsiPk(userId);
    this.userDeckIndexGsiSk = MetaKeys.userDeckIndexGsiSk(deckId);
    this.deckId = deckId;
    this.name = name;
    this.status = DeckStatus.IMPORTING;
  }
}

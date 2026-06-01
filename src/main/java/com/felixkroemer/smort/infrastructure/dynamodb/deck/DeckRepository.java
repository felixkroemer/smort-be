package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

@Repository
@RequiredArgsConstructor
public class DeckRepository {

  private final DynamoDbTable<NoteEntity> table;

  public void save(NoteEntity entity) {
    table.putItem(entity);
  }
}

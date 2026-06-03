package com.felixkroemer.smort.infrastructure.dynamodb.keys.partition;

import java.util.UUID;

public final class DeckKeys {

  public static String deckPk(UUID deckId) {
    return "DECK#" + deckId;
  }

  public static String userDeckIndexGsiPk(String userId) {
    return "USER#" + userId;
  }
}

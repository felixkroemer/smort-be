package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.util.UUID;

public final class MetaKeys {

  public static String metaSk() {
    return "META#";
  }

  public static String userDeckIndexGsiSk(UUID deckId) {
    return "DECK#" + deckId;
  }
}

package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.util.UUID;

public final class MetaKeys {

  public static String metaSk() {
    return "META#";
  }

  public static String metaPrefix() {
    return "META#";
  }

  public static String userDeckIndexGsiSk(UUID deckId) {
    return "DECK#" + deckId;
  }

  public static String userAnalysisIndexGsiSk(UUID analysisId) {
    return "ANALYSIS#" + analysisId;
  }
}

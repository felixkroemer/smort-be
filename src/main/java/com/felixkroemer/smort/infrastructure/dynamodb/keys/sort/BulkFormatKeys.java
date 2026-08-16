package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

public final class BulkFormatKeys {

  public static String bulkFormatPrefix() {
    return "META#BULKFORMAT#";
  }

  public static String analysisBulkFormatSk() {
    return "META#BULKFORMAT#ANALYSIS#";
  }

  public static String deckBulkFormatSk() {
    return "META#BULKFORMAT#DECK#";
  }
}

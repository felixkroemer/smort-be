package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.util.UUID;

public final class NoteKeys {

  public static String noteSk(Long noteId) {
    return "NOTE#" + noteId;
  }

  public static String noteSk(UUID noteId) {
    return "NOTE#" + noteId;
  }

  public static String notePrefix() {
    return "NOTE#";
  }
}

package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.util.UUID;

public final class NoteKeys {

  public static String derivedNoteSk(Long noteId) {
    return "NOTE#" + noteId;
  }

  public static String derivedNoteSk(UUID noteId) {
    return "NOTE#" + noteId;
  }

  public static String derivedNotePrefix() {
    return "NOTE#";
  }
}

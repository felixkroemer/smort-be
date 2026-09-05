package com.felixkroemer.smort.infrastructure.dynamodb.keys.partition;

public final class NotePartitionKeys {

  public static String userNoteIndexGsiPk(String userId) {
    return "USER#" + userId;
  }
}
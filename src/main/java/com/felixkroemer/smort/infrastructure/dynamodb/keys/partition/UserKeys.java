package com.felixkroemer.smort.infrastructure.dynamodb.keys.partition;

public final class UserKeys {

  public static String userPk(String userId) {
    return "USER#" + userId;
  }
}

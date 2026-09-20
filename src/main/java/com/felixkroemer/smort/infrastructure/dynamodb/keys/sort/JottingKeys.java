package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.util.UUID;

public final class JottingKeys {

  public static String jottingSk(UUID jottingId) {
    return "JOTTING#" + jottingId;
  }

  public static String jottingPrefix() {
    return "JOTTING#";
  }
}

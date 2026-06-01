package com.felixkroemer.smort.infrastructure.dynamodb.keys.partition;

import java.util.UUID;

public final class AnalysisKeys {

  public static String analysisPk(UUID analysisId) {
    return "ANALYSIS#" + analysisId;
  }
}

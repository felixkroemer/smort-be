package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

public final class UserSettingsKeys {

  public static String settingsSk() {
    return "SETTINGS#";
  }

  public static String templateSk(String templateId) {
    return "TEMPLATE#" + templateId;
  }

  public static String templatePrefix() {
    return "TEMPLATE#";
  }
}

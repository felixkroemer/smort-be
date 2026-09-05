package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.domain.chat.ChatUtil;

public enum SystemFormattingTemplate {

  DEFAULT("DEFAULT", "Default", ChatUtil.formattingRules());

  private final String id;
  private final String name;
  private final String content;

  SystemFormattingTemplate(String id, String name, String content) {
    this.id = id;
    this.name = name;
    this.content = content;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String content() {
    return content;
  }
}

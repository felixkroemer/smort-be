package com.felixkroemer.smort.domain.chat.llm;

import com.fasterxml.jackson.annotation.JsonClassDescription;

public class NoteChatTools {

  @JsonClassDescription("Store a updated ankiNote.")
  static class StoreNoteTool {
    public String front;
    public String back;
  }
}

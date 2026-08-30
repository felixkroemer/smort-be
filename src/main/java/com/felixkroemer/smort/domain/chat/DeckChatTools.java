package com.felixkroemer.smort.domain.chat;

import com.fasterxml.jackson.annotation.JsonClassDescription;

public class DeckChatTools {

  @JsonClassDescription("Draft a new ankiNote for the deck.")
  static class DraftNoteTool {
    public String front;
    public String back;
  }
}

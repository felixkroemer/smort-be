package com.felixkroemer.smort.domain.chat;

import com.fasterxml.jackson.annotation.JsonClassDescription;

public class DeckChatTools {

  @JsonClassDescription("Draft a new ankiNote for the deck.")
  static class DraftNoteTool {
    public String front;
    public String back;
  }

  @JsonClassDescription("Add a new ankiNote to the deck.")
  static class AddNoteTool {
    public String front;
    public String back;
  }
}

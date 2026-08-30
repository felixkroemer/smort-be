package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.common.exception.SmortException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum DeckChatToolType {
  DRAFT_NOTE(DeckChatTools.DraftNoteTool.class);
  private final Class<?> parserClass;

  static DeckChatToolType fromToolName(String name) {
    for (DeckChatToolType type : values()) {
      if (type.parserClass.getSimpleName().equals(name)) {
        return type;
      }
    }
    throw new SmortException("Unexpected tool called. toolName={}", name);
  }
}
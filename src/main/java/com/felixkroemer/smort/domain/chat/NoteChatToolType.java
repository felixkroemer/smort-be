package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.common.exception.SmortException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum NoteChatToolType {
  STORE_NOTE(NoteChatTools.StoreNoteTool.class);
  private final Class<?> parserClass;

  static NoteChatToolType fromToolName(String name) {
    for (NoteChatToolType type : values()) {
      if (type.parserClass.getSimpleName().equals(name)) {
        return type;
      }
    }
    throw new SmortException("Unexpected tool called. toolName={}", name);
  }
}

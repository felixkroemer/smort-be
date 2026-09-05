package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.domain.chat.ChatUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemFormattingTemplate {

  DEFAULT("DEFAULT", "Default", ChatUtil.formattingRules());

  private final String id;
  private final String name;
  private final String content;
}

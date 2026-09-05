package com.felixkroemer.smort.domain.user;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemFormattingTemplate {
  DEFAULT("DEFAULT", "Default", SystemFormattingTemplateRules.DEFAULT_FORMATTING_RULES);

  private final String id;
  private final String name;
  private final String content;

  public static Optional<SystemFormattingTemplate> fromId(String id) {
    return Arrays.stream(values()).filter(t -> t.getId().equals(id)).findFirst();
  }

  private static class SystemFormattingTemplateRules {
    private static final String DEFAULT_FORMATTING_RULES =
        """
        Output must be plain markdown. Never output HTML tags — not even a single one.
        Convert all HTML in the input to its markdown equivalent before outputting (e.g. <strong> → **, <ul>/<li> → - lists, <code> → `code`).
        When separating concatenated fields, use markdown headings (e.g. ## Definition, ## Example).
        Fix any obvious spelling and punctuation mistakes as long as the intended meaning remains unchanged.
        """;
  }
}

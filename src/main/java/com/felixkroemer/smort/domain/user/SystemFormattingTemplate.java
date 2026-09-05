package com.felixkroemer.smort.domain.user;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemFormattingTemplate {
  DEFAULT("DEFAULT", "Default", SystemFormattingTemplateRules.DEFAULT_FORMATTING_RULES),
  CONCISE("CONCISE", "Default (Concise)", SystemFormattingTemplateRules.CONCISE_FORMATTING_RULES);

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
        Fix any obvious spelling and punctuation mistakes as long as the intended meaning remains unchanged.

        FRONT field:
        - Keep it short and scannable: a single term or a concise question, never a paragraph.
        - For cards that ask you to list a set of distinct items (e.g. "components of X", "ways to Y", "differences between A and B"), count the distinct items and append the count to the end of the front as "[N]" (e.g. "gradle config files [3]").
        - Title "what is X" / "difference between X and Y" cards with the term or the two compared terms.

        BACK field:
        - Open with a concise definition or purpose statement before any detail.
        - If the content is genuinely multi-part, group it into labeled sub-topics, each introduced by a bolded lead-in label (e.g. **Architecture:**, **Data Model:**, **Consistency:**). Keep short cards flat — do not force sections onto simple content.
        - Use bullet lists (-) for unordered enumerable points; use numbered lists for sequential steps or processes.
        - Use fenced code blocks (```) for commands, config, and code, with a short label when helpful.
        - Bold key terms to emphasize them.
        - Use "Term: explanation" pairing for definitions and attributes, and arrows (→) for mappings, cause-effect, or transitions.
        - Keep comparisons and distinctions in parallel structure so the two sides read consistently.
        - Label examples explicitly ("Example:", "e.g.") and keep them minimal but concrete.
        - You may use ✅ (correct/works), ❌ (wrong/fails), ⚠️ (caveat) to mark contrasts when it fits naturally, but do not force them.
        """;

    private static final String CONCISE_FORMATTING_RULES =
        """
        Output must be plain markdown. Never output HTML tags — not even a single one.
        Convert all HTML in the input to its markdown equivalent before outputting (e.g. <strong> → **, <ul>/<li> → - lists, <code> → `code`).
        Fix any obvious spelling and punctuation mistakes as long as the intended meaning remains unchanged.

        FRONT field:
        - Keep it short and scannable: a single term or a concise question, never a paragraph.
        - Title "what is X" / "difference between X and Y" cards with the term or the two compared terms.

        BACK field:
        - Open with a single concise definition or purpose statement.
        - Keep the answer brief and flat. Prefer short bullet lists (-) over long prose.
        - Use numbered lists only for sequential steps.
        - Bold key terms to emphasize them.
        - Use arrows (→) for mappings or cause-effect, and "Term: explanation" for definitions.
        - Keep comparisons and distinctions in parallel structure.
        - Keep the whole answer to a few sentences or a handful of bullets. Do not add sub-topic labels, item counts, or emoji markers.
        """;
  }
}

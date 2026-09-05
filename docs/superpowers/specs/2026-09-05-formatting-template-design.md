# Design: Improve Default System Formatting Template

## Goal

Replace the default formatting instructions in `SystemFormattingTemplate.DEFAULT`
with a richer, more opinionated set of rules derived from the user's "general"
deck of well-formatted Anki cards. The new rules produce cards that match the
user's established style.

The existing template content is disregarded entirely; the new rules fully
replace it.

## Current state

`src/main/java/com/felixkroemer/smort/domain/user/SystemFormattingTemplate.java`
holds `DEFAULT_FORMATTING_RULES`, injected into the model prompt by
`ChatUtil.formatInstructions(...)`. Current content is minimal (plain markdown,
no HTML, convert HTML, headings for field separation, fix spelling).

## New DEFAULT formatting rules

```text
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
```

## Decisions

- **Count `[N]`:** The model derives and appends the count to the front for
  list/recall cards (counts the distinct items in the back).
- **Structure depth:** Labeled bolded sections only for genuinely multi-part
  cards; short cards stay flat.
- **Emphasis markers (✅ ❌ ⚠️):** Allowed but not required; used when they fit
  naturally.
- The old heading convention (`## Definition`, etc.) is not carried over; the
  bolded-lead-in approach replaces it.

## Scope

- Single-file change: `SystemFormattingTemplate.java` content of
  `DEFAULT_FORMATTING_RULES`.
- No test or refactor changes (tests only when explicitly requested).
- No build verification (per AGENTS.md the human owns compilation).

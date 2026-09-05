# Design: Improve Default System Formatting Template

## Goal

Improve the formatting instructions in `SystemFormattingTemplate` with richer,
more opinionated rules derived from the user's "general" deck of well-formatted
Anki cards. Two templates are provided:

- **Default** — dense, comprehensive cards matching the user's established style.
- **Default (Concise)** — a brief, flat variant without item counts, labeled
  sub-topics, or emoji markers.

The original `DEFAULT` content is disregarded entirely; the new rules fully
replace it.

## Current state

`src/main/java/com/felixkroemer/smort/domain/user/SystemFormattingTemplate.java`
holds formatting rules constants, injected into the model prompt by
`ChatUtil.formatInstructions(...)`. Original content was minimal (plain
markdown, no HTML, convert HTML, headings for field separation, fix spelling).

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

## New DEFAULT (Concise) formatting rules

A brief, flat variant. No `[N]` counts on the front, no `✅/❌/⚠️` markers, no
labeled sub-topics, and an explicit instruction to stay brief.

```text
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
```

## Decisions

- **Count `[N]`:** Only in **Default**. The model derives and appends the count
  to the front for list/recall cards. **Default (Concise)** omits counts.
- **Structure depth:** **Default** uses labeled bolded sections only for
  genuinely multi-part cards. **Default (Concise)** stays flat and brief.
- **Emphasis markers (✅ ❌ ⚠️):** Allowed but not required in **Default**;
  forbidden in **Default (Concise)**.
- The old heading convention (`## Definition`, etc.) is not carried over; the
  bolded-lead-in approach replaces it.

## Scope

- Change to `SystemFormattingTemplate.java`: update `DEFAULT_FORMATTING_RULES`
  content and add a new `CONCISE` enum constant with
  `CONCISE_FORMATTING_RULES`.
- No test or refactor changes (tests only when explicitly requested).
- No build verification (per AGENTS.md the human owns compilation).

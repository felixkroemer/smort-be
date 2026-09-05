# Improved Default Formatting Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `SystemFormattingTemplate.DEFAULT`'s formatting instructions with a richer, opinionated set of rules derived from the user's well-formatted general deck.

**Architecture:** A single-file content change to the Java enum `SystemFormattingTemplate`, updating the `DEFAULT_FORMATTING_RULES` text block. The rules are injected verbatim into the model prompt by `ChatUtil.formatInstructions(...)`, so no other code changes are needed.

**Tech Stack:** Java (text blocks), existing Spring Boot project.

## Global Constraints

- Per AGENTS.md: write tests only when explicitly asked — this plan adds no tests.
- Per AGENTS.md: do not run the build (`./mvnw compile`, `./mvnw test`). The human owns compilation.
- The old template content is disregarded entirely; it is fully replaced.
- Preserve the exact prompt structure: rules are inserted after the "Formatting rules (apply to both fields):" line in `ChatUtil.java`.

---

### Task 1: Update DEFAULT formatting rules

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/user/SystemFormattingTemplate.java:22-28`

**Interfaces:**
- Consumes: nothing (self-contained string constant).
- Produces: updated `SystemFormattingTemplate.DEFAULT.getContent()` value, consumed downstream by `ChatUtil.formatInstructions(...)` (unchanged).

- [ ] **Step 1: Replace the DEFAULT_FORMATTING_RULES content**

Edit `SystemFormattingTemplate.java` so the `DEFAULT_FORMATTING_RULES` text block (currently lines 22–28) reads:

```java
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
```

Note: The file must remain valid UTF-8. The `→`, `✅`, `❌`, `⚠️` characters and the `- ` bullet markers are valid inside a Java text block. The ```` ``` ```` sequence inside the text block does not terminate it — only `"""` does.

- [ ] **Step 2: Verify no build runs and no other files changed**

Run: `git diff --stat`
Expected: only `src/main/java/com/felixkroemer/smort/domain/user/SystemFormattingTemplate.java` modified.
Do not run the build (per AGENTS.md).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/user/SystemFormattingTemplate.java
git commit -m "feat: improve default system formatting rules"
```

# Chat Context in Instructions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all chat context into model `instructions` (deck name, note fields, current draft) and pass the user message through unchanged, with a "No draft note exists right now." fallback in the deck chat.

**Architecture:** `DeckChatContext` gains an `Optional<NoteSchema> draft` component, populated by `DeckService.chat` from `DraftNoteRepository`. `DeckChatService` and `NoteChatService` restructure their `CHAT_INSTRUCTIONS` text blocks to add context placeholders and set `.input(message)`. `NoteChatService` gets a dedicated ack-instructions constant to keep `acknowledgeStoreNoteToolCall` compiling.

**Tech Stack:** Java 25, Spring Boot, OpenAI Responses API (openai-java v4.29.0), DynamoDB enhanced client.

## Global Constraints

- Work only on feature branch `feat/chat-context-in-instructions`. Never touch `main`.
- Do NOT write tests (not requested).
- Do NOT run, fix, or debug the build (`./mvnw compile`, `./mvnw test`, etc.) — the human owns compilation and verifies later. Note in reports that compilation was skipped.
- Do not add code comments.
- Follow existing code style (4-space indent, `var`, text blocks, `String.join("\n", ...)` + `.toList()`).
- Commit after each task on the feature branch.

---

### Task 1: Add draft to DeckChatContext and populate it in DeckService.chat

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatContext.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java` (chat method, ~lines 167-187)

**Interfaces:**
- Consumes: `DraftNoteRepository.findDraftNote(UUID)` → `Optional<DraftNoteEntity>`; `DraftNoteEntity.getFront()/getBack()`; `NoteSchema(String front, String back)` (already imported in DeckService).
- Produces: `DeckChatContext(UUID deckId, String deckName, List<String> notes, Optional<NoteSchema> draft)` — Task 2 consumes `ctx.draft()`, `ctx.deckName()`, `ctx.notes()`.

- [ ] **Step 1: Update `DeckChatContext`**

Replace the entire contents of `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatContext.java` with:

```java
package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.domain.common.NoteSchema;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record DeckChatContext(
    UUID deckId, String deckName, List<String> notes, Optional<NoteSchema> draft)
    implements ChatContext {}
```

- [ ] **Step 2: Update `DeckService.chat` to fetch the draft**

In `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`, in the `chat(UUID deckId, String message)` method, replace:

```java
    var notes =
        deckRepository.findNotesByDeckId(deckId).stream().map(NoteEntity::getFront).toList();

    var ctx = new DeckChatContext(deckId, deck.getName(), notes);
```

with:

```java
    var notes =
        deckRepository.findNotesByDeckId(deckId).stream().map(NoteEntity::getFront).toList();

    var draft =
        draftNoteRepository
            .findDraftNote(deckId)
            .map(d -> new NoteSchema(d.getFront(), d.getBack()));

    var ctx = new DeckChatContext(deckId, deck.getName(), notes, draft);
```

`NoteSchema` is already imported in DeckService (line 14); `draftNoteRepository` field already exists (line 48).

- [ ] **Step 3: Verify the diff**

Run `git diff` and confirm only the intended two files changed and that the record's new component is constructed at exactly one call site (DeckService.chat). Grep for other `new DeckChatContext(` constructions to confirm none remain elsewhere.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/DeckChatContext.java src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: carry current draft in deck chat context"
```

Compilation was skipped per AGENTS.md (human owns build).

---

### Task 2: Embed deck name and draft in DeckChatService instructions

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java` (`CHAT_INSTRUCTIONS` constant and `chat` method, ~lines 22-59)

**Interfaces:**
- Consumes: `DeckChatContext.draft()` → `Optional<NoteSchema>` (Task 1); `ChatUtil.formattingRules()`.
- Produces: `chat(DeckChatContext ctx, String message, Optional<String> previousResponseId)` unchanged signature; input now equals `message` verbatim.

- [ ] **Step 1: Restructure `CHAT_INSTRUCTIONS`**

Replace the `CHAT_INSTRUCTIONS` constant in `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java` with:

```java
  private static final String CHAT_INSTRUCTIONS =
      """
      Your task is to assist the user in learning about and improving their Anki deck.
      You can discuss the deck's content, help identify gaps, and suggest improvements.

      Deck: %s

      When the user asks you to draft a new note for the deck, use the DraftNote tool.
      The "front" should be the question or term, the "back" the answer or explanation.
      Take the conversation into account: if the topic was discussed before, or the user asked
      for clarifications or adjustments, reflect that in the note, but keep it concise,
      not overly verbose.

      For the formatting, consider these rules:
      %s

      The deck currently contains these notes:
      %s

      Current draft note:
      %s
      """;
```

Placeholder order: `%s` = deck name, formatting rules, notes, draft section.

- [ ] **Step 2: Update the `chat` method body**

In the same file, replace the top of the `chat` method:

```java
    String fullInput = "Deck: " + ctx.deckName() + "\n\n" + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(
                CHAT_INSTRUCTIONS.formatted(
                    ChatUtil.formattingRules(), String.join("\n", ctx.notes())))
            .input(fullInput)
```

with:

```java
    var draftSection =
        ctx.draft()
            .map(d -> "Front: %s\nBack: %s".formatted(d.front(), d.back()))
            .orElse("No draft note exists right now.");

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(
                CHAT_INSTRUCTIONS.formatted(
                    ctx.deckName(),
                    ChatUtil.formattingRules(),
                    String.join("\n", ctx.notes()),
                    draftSection))
            .input(message)
```

Leave the rest of the method unchanged (tool call handling, ack).

- [ ] **Step 3: Verify the diff**

Run `git diff` and confirm: the deck-name prefix is gone from the input, `.input(message)` passes the message verbatim, and all four `%s` placeholders are supplied in `.formatted(...)`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java
git commit -m "feat: embed deck name and draft in deck chat instructions"
```

Compilation was skipped per AGENTS.md (human owns build).

---

### Task 3: Embed note fields in NoteChatService instructions and add ack constant

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java` (`CHAT_INSTRUCTIONS` constant, `chat` method, `acknowledgeStoreNoteToolCall` method)

**Interfaces:**
- Consumes: `NoteChatContext<?>.fields()` → `Map<String, String>`; `ChatUtil.formatInstructions()`.
- Produces: `chat(NoteChatContext<?> ctx, String message, Optional<String> previousResponseId)` unchanged signature; input equals `message` verbatim. New private constant `NOTE_ACK_INSTRUCTIONS`.

- [ ] **Step 1: Restructure `CHAT_INSTRUCTIONS` and add the ack constant**

Replace the `CHAT_INSTRUCTIONS` constant in `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java` with:

```java
  private static final String CHAT_INSTRUCTIONS =
      """
      Your task is to assist the user in fact-checking, learning about, and improving the anki ankiNote provided in the form of its fields.

      The ankiNote has these fields:
      %s

      When you are asked to edit one or multiple fields in any way, use the tool for updating notes.
      Then acknowledge with a short summary.

      For the formatting, consider these rules:
      %s
      """;

  private static final String NOTE_ACK_INSTRUCTIONS =
      """
      Confirm to the user that the note was updated. Keep it to one short sentence.
      """;
```

Placeholder order: `%s` = fields block, formatting rules.

- [ ] **Step 2: Update the `chat` method body**

In the same file, replace the top of the `chat` method:

```java
    String fullInput =
        "Fields:\n"
            + String.join(
                "\n",
                ctx.fields().entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .toList())
            + "\n\n"
            + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS.formatted(ChatUtil.formatInstructions()))
            .input(fullInput)
```

with:

```java
    String fieldsBlock =
        String.join(
            "\n",
            ctx.fields().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .toList());

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS.formatted(fieldsBlock, ChatUtil.formatInstructions()))
            .input(message)
```

Leave the rest of the method unchanged.

- [ ] **Step 3: Switch `acknowledgeStoreNoteToolCall` to the ack constant**

In the same file, in `acknowledgeStoreNoteToolCall`, replace:

```java
            .instructions(CHAT_INSTRUCTIONS.formatted(ChatUtil.formatInstructions()))
```

with:

```java
            .instructions(NOTE_ACK_INSTRUCTIONS)
```

- [ ] **Step 4: Verify the diff**

Run `git diff` and confirm: the `Fields:\n` prefix is gone from the input, `.input(message)` passes the message verbatim, both `%s` placeholders are supplied in `.formatted(...)`, and the ack method no longer references `CHAT_INSTRUCTIONS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java
git commit -m "feat: embed note fields in note chat instructions"
```

Compilation was skipped per AGENTS.md (human owns build).

---

## Final Verification

After all tasks are committed on `feat/chat-context-in-instructions`:

1. `git status` — clean working tree, only feature-branch commits ahead of main.
2. `git log --oneline main..HEAD` — three feature commits plus the spec commit.
3. Report to the human: compilation skipped per AGENTS.md; ask them to build.
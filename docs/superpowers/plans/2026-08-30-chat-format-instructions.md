# Chat Format Instructions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thread each entity's `formatInstructions` setting through the three chat flows (deck chat, deck note chat, analysis note chat) so chat uses the per-entity setting instead of the hardcoded default, mirroring how `formatNote` already threads it.

**Architecture:** Add one `Optional<String> formatInstructions` parameter at each hop of the chat call chain — `ChatOrchestrationService.noteChat`/`deckChat` → `NoteChatService.chat`/`DeckChatService.chat` — and substitute it with `ChatUtil.formatInstructions(formatInstructions)` in the prompt. The three caller services fetch the setting from the entity's settings accessor and pass it through.

**Tech Stack:** Java, Spring Boot (services + Lombok), OpenAI Responses API (prompt construction).

## Global Constraints

- Keep the threading symmetric with the existing `formatNote` flow (`NoteChatService.formatNote(Map, Optional<String>)`, `ChatOrchestrationService.formatNote(..., Optional<String>, ...)`), which is the reference pattern.
- Substitution in both chat services is `ChatUtil.formatInstructions(formatInstructions)` (full instruction block, formatNote-style). This changes `DeckChatService`'s default prompt from rules-only to the full block — intended (matches `NoteChatService.chat`'s existing behavior).
- Callers fetch the setting via the dedicated settings accessors: `analysisService.getAnalysisSettings(analysisId).formatInstructions()` and `deckService.getDeckSettings(deckId).formatInstructions()`.
- Do NOT write tests (AGENTS.md: write tests only when explicitly asked).
- Do NOT run, fix, or debug the build (`./mvnw compile`, `./mvnw test`, etc.); compilation is verified later by the human. Note in the report that compilation was skipped.
- All work happens on the feature branch `feat/chat-format-instructions` (branched from `feat/deck-format-settings`).
- Commit message style: lowercase conventional prefixes (`feat:`), matching the repo history.
- No code comments unless the surrounding code already has them; follow google-java-format (2-space indent, trailing newline at EOF).

---

### Task 1: Thread `formatInstructions` through the chat chain

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java` (the `chat` method)
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java` (the `chat` method)
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java` (`noteChat` and `deckChat`)
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java` (the `chat` method)
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java` (the `chat` method)
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java` (the `chat` method)

**Interfaces:**
- Consumes: existing `ChatUtil.formatInstructions(Optional<String>)` (returns the full instruction block), `AnalysisSettings.formatInstructions()`, `DeckSettings.formatInstructions()`.
- Produces: the complete threaded chain. `ChatOrchestrationService.noteChat(String, NoteChatContext<?>, String, Optional<String>, Map<...>)`, `ChatOrchestrationService.deckChat(String, DeckChatContext, String, Optional<String>, Map<...>)`, `NoteChatService.chat(NoteChatContext<?>, String, Optional<String>, Optional<String>)`, `DeckChatService.chat(DeckChatContext, String, Optional<String>, Optional<String>)`. Nothing downstream builds on these signatures within this plan.

- [ ] **Step 1: Add the parameter to `NoteChatService.chat`**

In `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java`, change the `chat` signature to:

```java
  public ChatMessage chat(
      NoteChatContext<?> ctx,
      String message,
      Optional<String> formatInstructions,
      Optional<String> previousResponseId) {
```

Then replace the instructions line in the `ResponseCreateParams` builder:

```java
            .instructions(CHAT_INSTRUCTIONS.formatted(fieldsBlock, ChatUtil.formatInstructions()))
```

with:

```java
            .instructions(
                CHAT_INSTRUCTIONS.formatted(fieldsBlock, ChatUtil.formatInstructions(formatInstructions)))
```

`Optional` is already imported.

- [ ] **Step 2: Add the parameter to `DeckChatService.chat`**

In `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java`, change the `chat` signature to:

```java
  public ChatMessage chat(
      DeckChatContext ctx,
      String message,
      Optional<String> formatInstructions,
      Optional<String> previousResponseId) {
```

Then replace the formatting substitution in the `CHAT_INSTRUCTIONS.formatted(...)` call:

```java
                    ChatUtil.formattingRules(),
```

with:

```java
                    ChatUtil.formatInstructions(formatInstructions),
```

`Optional` is already imported.

- [ ] **Step 3: Add the parameter to `ChatOrchestrationService.noteChat` and `deckChat`**

In `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java`, change the `noteChat` signature to:

```java
  public List<ChatMessageEntity> noteChat(
      String pk,
      NoteChatContext<?> ctx,
      String message,
      Optional<String> formatInstructions,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
```

and its call to the note chat service from:

```java
    var chatMessage = noteChatService.chat(ctx, message, latestChatMessageResponseId);
```

to:

```java
    var chatMessage =
        noteChatService.chat(ctx, message, formatInstructions, latestChatMessageResponseId);
```

Change the `deckChat` signature to:

```java
  public List<ChatMessageEntity> deckChat(
      String pk,
      DeckChatContext ctx,
      String message,
      Optional<String> formatInstructions,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
```

and its call from:

```java
    var chatMessage = deckChatService.chat(ctx, message, latestChatMessageResponseId);
```

to:

```java
    var chatMessage =
        deckChatService.chat(ctx, message, formatInstructions, latestChatMessageResponseId);
```

`Optional` is already imported.

- [ ] **Step 4: Fetch and pass the setting in `AnkiNoteService.chat`**

In `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java`, in the `chat` method, add the settings fetch right after the existing `var content = getContent(analysisId, noteId);` line:

```java
    var formatInstructions = analysisService.getAnalysisSettings(analysisId).formatInstructions();
```

Then change the orchestration call from:

```java
    return chatOrchestrationService.noteChat(
        AnalysisKeys.analysisPk(analysisId), ctx, message, toolHandlers);
```

to:

```java
    return chatOrchestrationService.noteChat(
        AnalysisKeys.analysisPk(analysisId), ctx, message, formatInstructions, toolHandlers);
```

`analysisService` is already an injected field; `Optional` is already imported.

- [ ] **Step 5: Fetch and pass the setting in `NoteService.chat`**

In `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java`, in the `chat` method, add the settings fetch right after the `var note = ...` lookup (before the `var ctx = ...` line):

```java
    var formatInstructions = deckService.getDeckSettings(deckId).formatInstructions();
```

Then change the orchestration call from:

```java
    return chatOrchestrationService.noteChat(DeckKeys.deckPk(deckId), ctx, message, toolHandlers);
```

to:

```java
    return chatOrchestrationService.noteChat(
        DeckKeys.deckPk(deckId), ctx, message, formatInstructions, toolHandlers);
```

`deckService` is already an injected field; `Optional` is already imported.

- [ ] **Step 6: Fetch and pass the setting in `DeckService.chat`**

In `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`, in the `chat` method, add the settings fetch right after the existing `var deck = getMeta(deckId);` line:

```java
    var formatInstructions = getDeckSettings(deckId).formatInstructions();
```

Then change the orchestration call from:

```java
    return chatOrchestrationService.deckChat(DeckKeys.deckPk(deckId), ctx, message, toolHandlers);
```

to:

```java
    return chatOrchestrationService.deckChat(
        DeckKeys.deckPk(deckId), ctx, message, formatInstructions, toolHandlers);
```

`getDeckSettings` is a method on this same class; `Optional` is already imported.

- [ ] **Step 7: Self-review and commit**

Verify against the brief: all 6 files changed; no leftover 3-arg calls to `noteChatService.chat` / `deckChatService.chat`; no leftover 4-arg calls to `chatOrchestrationService.noteChat` / `.deckChat`; `Optional` imports still present where used. Commit:

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: use entity format instructions in chat flows"
```

---

### Task 2: Final report

- [ ] **Step 1: Report completion**

Summarize what was implemented (the threaded chain for deck chat, deck note chat, and analysis note chat; substitution via `ChatUtil.formatInstructions(formatInstructions)`; `DeckChatService` prompt now embeds the full instruction block). Note that compilation was skipped per AGENTS.md. Confirm all commits are on `feat/chat-format-instructions` and push:

```bash
git push -u origin feat/chat-format-instructions
```

Do NOT merge into main; leave that to the human.
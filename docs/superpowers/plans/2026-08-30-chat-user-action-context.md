# Chat User Action Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Inject the latest consecutive run of user-initiated chat messages (as JSON: `toolName` + `arguments`) into the model instructions at the 3 chat-send points.

**Architecture:** A new `UserActionContextService` component queries `ChatRepository.findAll`, computes the newest-first consecutive run of `userInitiated == true` messages, and returns an `Optional<String>` section of JSON. `ChatOrchestrationService` (which has `pk`/`entityId`) builds the context once per entry point and passes it into the chat services, which append it to their instructions via a new `ChatUtil.appendUserActions` helper.

**Tech Stack:** Java 21, Spring, Lombok, AWS DynamoDB Enhanced Client, OpenAI Responses API, Jackson.

## Global Constraints

- Work on feature branch `feat/chat-user-action-context`. Commit after every task.
- Do NOT write tests (per AGENTS.md: tests only when explicitly requested).
- Do NOT run, fix, or debug the build (`./mvnw compile`, `./mvnw test`). Compilation is skipped per AGENTS.md; note this in reports.
- Follow existing code style: Lombok `@RequiredArgsConstructor`, `var`, Java 21 (`List.reversed()`).
- The tool-acknowledgement model calls (`acknowledgeStoreNoteToolCall`, `acknowledgeDraftNoteToolCall`) receive NO context.

---

### Task 1: `UserActionContextService` + `ChatUtil.appendUserActions`

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/chat/UserActionContextService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatUtil.java` (add helper at end of class)

**Interfaces:**
- Produces: `UserActionContextService.buildContext(String pk, T entityId) -> Optional<String>` — returns `Optional.empty()` when there is no consecutive run of user-initiated messages or no entry has a `toolName`; otherwise `Optional.of("Recent user actions:\n<json array>")`.
- Produces: `ChatUtil.appendUserActions(String instructions, Optional<String> userActionContext) -> String` — appends `"\n\n" + context` when present, else returns `instructions` unchanged.

- [ ] **Step 1: Create `UserActionContextService`**

`src/main/java/com/felixkroemer/smort/domain/chat/UserActionContextService.java`:

```java
package com.felixkroemer.smort.domain.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserActionContextService {

  private final ChatRepository chatRepository;
  private final ObjectMapper mapper;

  public <T> Optional<String> buildContext(String pk, T entityId) {
    var messages = chatRepository.findAll(pk, entityId);

    var consecutiveRun = new ArrayList<ChatMessageEntity>();
    for (var message : messages) {
      if (!message.isUserInitiated()) {
        break;
      }
      consecutiveRun.add(message);
    }

    if (consecutiveRun.isEmpty()) {
      return Optional.empty();
    }

    var entries =
        consecutiveRun.reversed().stream()
            .filter(m -> m.getToolName().isPresent())
            .map(
                m ->
                    Map.<String, Object>of(
                        "toolName", m.getToolName().get(), "arguments", m.getArguments()))
            .toList();

    if (entries.isEmpty()) {
      return Optional.empty();
    }

    try {
      return Optional.of("Recent user actions:\n" + mapper.writeValueAsString(entries));
    } catch (JsonProcessingException e) {
      throw new SmortException("Could not serialize user action context", e);
    }
  }
}
```

Note: `chatRepository.findAll` already returns messages newest-first by `createdAt`, so the loop collects the latest consecutive run of `isUserInitiated() == true` messages and stops at the first non-user-initiated one. `List.reversed()` restores chronological order before rendering.

- [ ] **Step 2: Add `appendUserActions` to `ChatUtil`**

Add at the end of `src/main/java/com/felixkroemer/smort/domain/chat/ChatUtil.java` (class currently ends after `getResponseOutputText`; `Optional` is already imported):

```java
  public static String appendUserActions(String instructions, Optional<String> userActionContext) {
    return userActionContext.map(ctx -> instructions + "\n\n" + ctx).orElse(instructions);
  }
```

- [ ] **Step 3: Review diff**

Run: `git diff`
Expected: only `UserActionContextService.java` (new) and `ChatUtil.java` (helper added).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/UserActionContextService.java src/main/java/com/felixkroemer/smort/domain/chat/ChatUtil.java
git commit -m "feat: add user action context service"
```

---

### Task 2: Wire context into note chat (formatNote + noteChat)

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java`

**Interfaces:**
- Consumes: `UserActionContextService.buildContext` and `ChatUtil.appendUserActions` from Task 1.
- Produces: `NoteChatService.formatNote(Map<String,String> fields, Optional<String> formatInstructions, Optional<String> userActionContext)` and `NoteChatService.chat(NoteChatContext<?> ctx, String message, Optional<String> formatInstructions, Optional<String> previousResponseId, Optional<String> userActionContext)`.

- [ ] **Step 1: Add `userActionContext` param to `NoteChatService.formatNote`**

In `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java`, change the signature:

```java
  public StoreNoteToolChatMessage formatNote(
      Map<String, String> fields,
      Optional<String> formatInstructions,
      Optional<String> userActionContext) {
```

and replace the `.instructions(ChatUtil.formatInstructions(formatInstructions))` line with:

```java
            .instructions(
                ChatUtil.appendUserActions(
                    ChatUtil.formatInstructions(formatInstructions), userActionContext))
```

- [ ] **Step 2: Add `userActionContext` param to `NoteChatService.chat`**

Change the signature:

```java
  public ChatMessage chat(
      NoteChatContext<?> ctx,
      String message,
      Optional<String> formatInstructions,
      Optional<String> previousResponseId,
      Optional<String> userActionContext) {
```

and replace the `.instructions(...)` block:

```java
            .instructions(
                CHAT_INSTRUCTIONS.formatted(fieldsBlock, ChatUtil.formatInstructions(formatInstructions)))
```

with:

```java
            .instructions(
                ChatUtil.appendUserActions(
                    CHAT_INSTRUCTIONS.formatted(
                        fieldsBlock, ChatUtil.formatInstructions(formatInstructions)),
                    userActionContext))
```

- [ ] **Step 3: Inject `UserActionContextService` into `ChatOrchestrationService`**

Add the field to the class body in `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java` (constructor is Lombok-generated):

```java
  private final UserActionContextService userActionContextService;
```

- [ ] **Step 4: Pass context in `formatNote`**

In `formatNote`, insert the context build before the `noteChatService.formatNote(...)` call:

```java
    var userActionContext = userActionContextService.buildContext(pk, entityId);
    var storeNoteToolChatMessage =
        noteChatService.formatNote(content, formatInstructions, userActionContext);
```

- [ ] **Step 5: Pass context in `noteChat`**

In `noteChat`, before the `noteChatService.chat(...)` call:

```java
    var userActionContext = userActionContextService.buildContext(pk, ctx.noteId());
```

and update the call:

```java
    var chatMessage =
        noteChatService.chat(
            ctx, message, formatInstructions, latestChatMessageResponseId, userActionContext);
```

- [ ] **Step 6: Review diff**

Run: `git diff`
Expected: `NoteChatService.java` (2 signature changes + 2 instruction wraps) and `ChatOrchestrationService.java` (1 field + 2 call sites). No other files changed.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java
git commit -m "feat: provide user action context in note chat instructions"
```

---

### Task 3: Wire context into deck chat

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java`

**Interfaces:**
- Consumes: `UserActionContextService.buildContext` and `ChatUtil.appendUserActions` from Task 1.
- Produces: `DeckChatService.chat(DeckChatContext ctx, String message, Optional<String> formatInstructions, Optional<String> previousResponseId, Optional<String> userActionContext)`.

- [ ] **Step 1: Add `userActionContext` param to `DeckChatService.chat`**

In `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java`, change the signature:

```java
  public ChatMessage chat(
      DeckChatContext ctx,
      String message,
      Optional<String> formatInstructions,
      Optional<String> previousResponseId,
      Optional<String> userActionContext) {
```

and wrap the existing `.instructions(...)` call:

```java
            .instructions(
                ChatUtil.appendUserActions(
                    CHAT_INSTRUCTIONS.formatted(
                        ctx.deckName(),
                        ChatUtil.formatInstructions(formatInstructions),
                        String.join("\n", ctx.notes()),
                        draftSection),
                    userActionContext))
```

- [ ] **Step 2: Pass context in `deckChat`**

In `ChatOrchestrationService.deckChat`, before the `deckChatService.chat(...)` call:

```java
    var userActionContext = userActionContextService.buildContext(pk, ctx.deckId());
```

and update the call:

```java
    var chatMessage =
        deckChatService.chat(
            ctx, message, formatInstructions, latestChatMessageResponseId, userActionContext);
```

- [ ] **Step 3: Review diff**

Run: `git diff`
Expected: `DeckChatService.java` (1 signature change + 1 instruction wrap) and `ChatOrchestrationService.java` (1 call site).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java
git commit -m "feat: provide user action context in deck chat instructions"
```

---

## Post-Plan Verification (human)

- Compile and test (`./mvnw compile`, `./mvnw test`) — skipped by the implementing subagent per AGENTS.md; the human owns this.
- Manual sanity: in note chat, manually format a note (creates a `userInitiated=true` STORE_NOTE tool call), then send a chat message; the model instructions should contain `Recent user actions:` with the JSON entry.
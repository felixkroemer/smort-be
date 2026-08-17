# Deck Chat Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add chat infrastructure for Decks (imported decks) similar to Notes, enabling future features like passing note titles to the LLM to find gaps.

**Architecture:** Rename `noteId` to `entityId` across chat infrastructure, introduce a `ChatContext` sealed interface to differentiate note vs deck chat, split `ChatService.chat()` and `ChatOrchestrationService.chat()` into context-specific methods, and add deck chat endpoints to `DeckService`/`DeckController`.

**Tech Stack:** Java, Spring Boot, DynamoDB Enhanced Client, OpenAI Responses API

## Global Constraints

- Branch: `feat/deck-chat-infrastructure` (already created)
- Do not run build/compile commands (`./mvnw compile`, etc.)
- Follow existing code conventions (Lombok, MapStruct, sealed interfaces)

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/ChatKeys.java` | Modify | Rename `noteId` → `entityId` |
| `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/chat/ChatMessageEntity.java` | Modify | Rename `noteId` field → `entityId` |
| `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/chat/ChatRepository.java` | Modify | Rename `noteId` params → `entityId` |
| `src/main/java/com/felixkroemer/smort/domain/chat/ChatContext.java` | Create | Sealed interface for chat contexts |
| `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatContext.java` | Create | Record for note chat context |
| `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatContext.java` | Create | Record for deck chat context |
| `src/main/java/com/felixkroemer/smort/domain/chat/ChatService.java` | Modify | Add overloaded `chat()` for `NoteChatContext` and `DeckChatContext` |
| `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java` | Modify | Rename params, split `chat()` into `noteChat()` and `deckChat()` |
| `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java` | Modify | Update to use `NoteChatContext` |
| `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java` | Modify | Update to use `NoteChatContext` |
| `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java` | Modify | Add `chat()` and `getChat()` methods |
| `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java` | Modify | Add deck chat endpoints |

---

### Task 1: Rename `noteId` to `entityId` in chat infrastructure

Pure rename across 4 files. No behavior change.

**Files:**
- Modify: `ChatKeys.java:7-17`
- Modify: `ChatMessageEntity.java:25-51,54-72,75-97`
- Modify: `ChatRepository.java:22-43`
- Modify: `ChatOrchestrationService.java:29-31,83-98,100-116,118-157,159-175`

- [ ] **Step 1: Rename in ChatKeys.java**

In `ChatKeys.java`, rename parameter `noteId` to `entityId` in all three methods:

```java
public static String chatMessageSk(String entityId, Instant createdAt, String responseId, boolean userInitiated) {
    return "CHAT#" + (userInitiated ? "U#" : "C#") + entityId + "#" + createdAt + "#" + responseId;
}

public static <T> String llmChatMessagesPrefix(T entityId) {
    return "CHAT#C#" + entityId + "#";
}

public static <T> String userChatMessagesPrefix(T entityId) {
    return "CHAT#U#" + entityId + "#";
}
```

- [ ] **Step 2: Rename in ChatMessageEntity.java**

Rename the field and constructor parameter from `noteId` to `entityId`. Update the two static factory methods' parameters from `noteId` to `entityId`. Update the constructor body `this.noteId = noteId;` to `this.entityId = entityId;` and the `chatMessageSk` call to use `entityId`.

- [ ] **Step 3: Rename in ChatRepository.java**

Rename parameter `noteId` to `entityId` in `findLatestChatMessage()` and `findAll()` methods.

- [ ] **Step 4: Rename in ChatOrchestrationService.java**

Rename parameter `noteId` to `entityId` in `getChat()`, `chat()`, `handleChatMessageResponse()`, `handleStoreNoteToolResponse()`, and `handleChatMessageTextResponse()` methods.

- [ ] **Step 5: Verify callers compile**

Check that `NoteService.java:61`, `AnkiNoteService.java:86`, `DeckController.java:104` still compile (they pass UUID/Long values to generic `<T>` params, so no changes needed yet).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/ChatKeys.java \
       src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/chat/ChatMessageEntity.java \
       src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/chat/ChatRepository.java \
       src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java
git commit -m "refactor: rename noteId to entityId in chat infrastructure"
```

---

### Task 2: Create ChatContext sealed interface and records

Create 3 new files in `domain/chat/`.

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/chat/ChatContext.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatContext.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatContext.java`

- [ ] **Step 1: Create ChatContext.java**

```java
package com.felixkroemer.smort.domain.chat;

public sealed interface ChatContext permits NoteChatContext, DeckChatContext {}
```

- [ ] **Step 2: Create NoteChatContext.java**

```java
package com.felixkroemer.smort.domain.chat;

import java.util.Map;
import java.util.UUID;

public record NoteChatContext(UUID noteId, Map<String, String> fields) implements ChatContext {}
```

- [ ] **Step 3: Create DeckChatContext.java**

```java
package com.felixkroemer.smort.domain.chat;

import java.util.UUID;

public record DeckChatContext(UUID deckId, String deckName) implements ChatContext {}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/ChatContext.java \
       src/main/java/com/felixkroemer/smort/domain/chat/NoteChatContext.java \
       src/main/java/com/felixkroemer/smort/domain/chat/DeckChatContext.java
git commit -m "feat: add ChatContext sealed interface with NoteChatContext and DeckChatContext"
```

---

### Task 3: Split ChatService.chat() into overloaded methods

Modify `ChatService` to accept `NoteChatContext` or `DeckChatContext` and conditionally include tools.

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatService.java:132-180`

**Interfaces:**
- Consumes: `NoteChatContext`, `DeckChatContext` (from Task 2)
- Produces: Two overloaded `chat()` methods returning `ChatMessage`

- [ ] **Step 1: Add overloaded chat() for NoteChatContext**

Rename the existing `chat(Map<String,String>, String, Optional<String>)` method to accept `NoteChatContext`:

```java
public ChatMessage chat(NoteChatContext ctx, String message, Optional<String> previousResponseId) {
    String fullInput =
        "Fields:\n"
            + String.join(
                "\n",
                ctx.fields().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList())
            + "\n\n"
            + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(
                CHAT_INSTRUCTIONS.formatted(FORMATTING_INSTRUCTION.formatted(FORMATTING_RULES)))
            .input(fullInput)
            .previousResponseId(previousResponseId)
            .model(model)
            .addTool(StoreNoteTool.class)
            .build();

    var response = openAIClient.responses().create(params);
    var output = response.output();

    var responseOutputItem =
        output.stream()
            .reduce(
                (a, b) -> {
                    throw new SmortException("Received multiple output items");
                })
            .orElseThrow(() -> new SmortException("Received no output items"));

    var meta = new ChatMessageMeta(response.id(), response.previousResponseId(), Instant.now());

    if (responseOutputItem.isFunctionCall()) {
        var responseFunctionToolCall = responseOutputItem.asFunctionCall();
        var storeNoteToolCall = responseFunctionToolCall.arguments(StoreNoteTool.class);
        return new StoreNoteToolChatMessage(
            StoreNoteTool.class.getName(),
            responseFunctionToolCall.callId(),
            storeNoteToolCall.front,
            storeNoteToolCall.back,
            meta);
    } else if (responseOutputItem.isMessage()) {
        ResponseOutputText outputText = getResponseOutputText(responseOutputItem.asMessage());
        return new TextChatMessage(outputText.text(), meta);
    } else {
        throw new SmortException("Unexpected response output item type");
    }
}
```

- [ ] **Step 2: Add overloaded chat() for DeckChatContext**

```java
public ChatMessage chat(DeckChatContext ctx, String message, Optional<String> previousResponseId) {
    String fullInput =
        "Deck: " + ctx.deckName() + "\n\n" + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(
                CHAT_INSTRUCTIONS.formatted(FORMATTING_INSTRUCTION.formatted(FORMATTING_RULES)))
            .input(fullInput)
            .previousResponseId(previousResponseId)
            .model(model)
            .build();

    var response = openAIClient.responses().create(params);
    var output = response.output();

    var responseOutputItem =
        output.stream()
            .reduce(
                (a, b) -> {
                    throw new SmortException("Received multiple output items");
                })
            .orElseThrow(() -> new SmortException("Received no output items"));

    var meta = new ChatMessageMeta(response.id(), response.previousResponseId(), Instant.now());

    if (responseOutputItem.isMessage()) {
        ResponseOutputText outputText = getResponseOutputText(responseOutputItem.asMessage());
        return new TextChatMessage(outputText.text(), meta);
    } else {
        throw new SmortException("Unexpected response output item type");
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/ChatService.java
git commit -m "feat: split ChatService.chat() into NoteChatContext and DeckChatContext overloads"
```

---

### Task 4: Split ChatOrchestrationService.chat() and update callers

Split the `chat()` method into `noteChat()` and `deckChat()`, then update `NoteService` and `AnkiNoteService`.

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java:83-98`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java:55-70`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java:83-97`

**Interfaces:**
- Consumes: `NoteChatContext`, `DeckChatContext` (Task 2), `ChatService.chat(NoteChatContext, ...)` and `ChatService.chat(DeckChatContext, ...)` (Task 3)
- Produces: `noteChat()` and `deckChat()` methods in `ChatOrchestrationService`

- [ ] **Step 1: Replace chat() with noteChat() and deckChat() in ChatOrchestrationService**

Replace the existing `chat()` method (lines 83-98) with:

```java
public List<ChatMessageEntity> noteChat(
    String pk,
    NoteChatContext ctx,
    String message,
    TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler) {

    var latestChatMessage = chatRepository.findLatestChatMessage(pk, ctx.noteId());
    var latestChatMessageResponseId =
        latestChatMessage.map(AbstractChatMessageEntity::getResponseId);

    var chatMessage = chatService.chat(ctx, message, latestChatMessageResponseId);

    return handleChatMessageResponse(
        chatMessage, pk, ctx.noteId(), message, latestChatMessageResponseId, storeNoteHandler);
}

public List<ChatMessageEntity> deckChat(
    String pk,
    DeckChatContext ctx,
    String message) {

    var latestChatMessage = chatRepository.findLatestChatMessage(pk, ctx.deckId());
    var latestChatMessageResponseId =
        latestChatMessage.map(AbstractChatMessageEntity::getResponseId);

    var chatMessage = chatService.chat(ctx, message, latestChatMessageResponseId);

    return handleChatMessageResponse(
        chatMessage, pk, ctx.deckId(), message, latestChatMessageResponseId,
        (tx, front, back) -> {});
}
```

- [ ] **Step 2: Update NoteService.chat() to use NoteChatContext**

```java
public List<ChatMessageEntity> chat(UUID deckId, UUID noteId, String message) {
    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new NotFoundException("Note not found. id={}", noteId));

    var ctx = new NoteChatContext(noteId, note.getContent());
    return chatOrchestrationService.noteChat(
        DeckKeys.deckPk(deckId),
        ctx,
        message,
        (tx, front, back) -> {
            deckRepository.saveNoteInTx(
                tx, noteEntityMapper.toNoteEntity(deckId, noteId, new NoteSchema(front, back)));
        });
}
```

- [ ] **Step 3: Update AnkiNoteService.chat() to use NoteChatContext**

```java
public List<ChatMessageEntity> chat(UUID analysisId, Long noteId, String message) {
    var content = getContent(analysisId, noteId);

    var ctx = new NoteChatContext(noteId, content);
    return chatOrchestrationService.noteChat(
        AnalysisKeys.analysisPk(analysisId),
        ctx,
        message,
        (tx, front, back) -> {
            derivedNoteRepository.saveInTx(
                tx,
                derivedNoteEntityMapper.toDerivedNoteEntity(
                    analysisId, noteId, new NoteSchema(front, back)));
        });
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java \
       src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java \
       src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java
git commit -m "feat: split ChatOrchestrationService.chat() into noteChat() and deckChat()"
```

---

### Task 5: Add deck chat methods to DeckService

Add `chat()` and `getChat()` methods to `DeckService`.

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`

**Interfaces:**
- Consumes: `DeckChatContext` (Task 2), `ChatOrchestrationService.deckChat()` (Task 4)
- Produces: `DeckService.chat()` and `DeckService.getChat()`

- [ ] **Step 1: Add chat() and getChat() to DeckService**

Add these imports and methods to `DeckService`:

```java
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.domain.chat.DeckChatContext;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
```

Add methods (after `deleteNote`):

```java
private final ChatOrchestrationService chatOrchestrationService;

public List<ChatMessageEntity> chat(UUID deckId, String message) {
    var deck =
        deckRepository
            .findDeckMetaByDeckId(deckId)
            .orElseThrow(() -> new NotFoundException("Could not find deck. deckId={}", deckId));

    var ctx = new DeckChatContext(deckId, deck.getName());
    return chatOrchestrationService.deckChat(DeckKeys.deckPk(deckId), ctx, message);
}

public List<ChatMessageEntity> getChat(UUID deckId) {
    return chatOrchestrationService.getChat(DeckKeys.deckPk(deckId), deckId);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: add chat() and getChat() methods to DeckService"
```

---

### Task 6: Add deck chat endpoints to DeckController

Add `POST /{deckId}/chat` and `GET /{deckId}/chat` endpoints.

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java:92-106`

**Interfaces:**
- Consumes: `DeckService.chat()`, `DeckService.getChat()` (Task 5)
- Produces: Two new REST endpoints

- [ ] **Step 1: Add deck chat endpoints**

Add these endpoints to `DeckController` (after the existing note chat endpoints, before `deleteDeck`):

```java
@PostMapping("/{deckId}/chat")
public List<ChatMessageResponse> postDeckChatMessage(
    @PathVariable("deckId") UUID deckId,
    @RequestBody ChatMessageRequest chatMessageRequest) {
    var chatMessageResponses = deckService.chat(deckId, chatMessageRequest.message());
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
}

@GetMapping("/{deckId}/chat")
public List<ChatMessageResponse> getDeckChat(@PathVariable("deckId") UUID deckId) {
    var chatMessageResponses = deckService.getChat(deckId);
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add deck chat endpoints to DeckController"
```

---

### Task 7: Final cleanup and push

- [ ] **Step 1: Verify all changes are committed**

```bash
git status
git log --oneline -7
```

- [ ] **Step 2: Push feature branch**

```bash
git push -u origin feat/deck-chat-infrastructure
```

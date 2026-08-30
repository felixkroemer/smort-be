# Confirm and Add Draft Note Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated endpoint that confirms-and-adds the drafted deck note to the deck, clears the draft, stores a user-initiated ADD_NOTE chat message, and returns it to the user.

**Architecture:** `DeckController` exposes `POST /decks/{deckId}/draft-note/store` which calls a new `DeckService.storeDraftNote(deckId)`. That method loads the draft (404 if absent), builds a new `NoteEntity` from the draft's front/back, builds a synthetic `ADD_NOTE` tool-call chat message (`userInitiated=true`, generated UUID responseId/callId), and commits note + draft-delete + chat-message in one DynamoDB transaction. `DeckChatToolType.ADD_NOTE` + a placeholder `AddNoteTool` parser class provide the tool name; `DraftNoteRepository.deleteInTx` clears the draft in the transaction.

**Tech Stack:** Java 21, Spring, Lombok, AWS DynamoDB Enhanced Client, MapStruct.

## Global Constraints

- Work on feature branch `feat/confirm-add-draft-note`. Commit after every task.
- Do NOT write tests (per AGENTS.md: tests only when explicitly requested).
- Do NOT run, fix, or debug the build (`./mvnw compile`, `./mvnw test`). Compilation is skipped per AGENTS.md; note this in reports.
- Follow existing code style: Lombok `@RequiredArgsConstructor`, `var`, Java 21.
- The ADD_NOTE message uses `userInitiated=true`, empty `message`, empty `previousResponseId`, and generated UUID `responseId`/`callId` (never used for the OpenAI chain because `findLatestChatMessage` queries only the `CHAT#C#` prefix).

---

### Task 1: `ADD_NOTE` tool type + placeholder parser class

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatTools.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatToolType.java`

**Interfaces:**
- Produces: `DeckChatToolType.ADD_NOTE` enum constant (tool-name string `"ADD_NOTE"`), backed by `DeckChatTools.AddNoteTool`. Not registered as an OpenAI tool.

- [ ] **Step 1: Add `AddNoteTool` placeholder class to `DeckChatTools`**

In `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatTools.java`, after the `DraftNoteTool` class, add:

```java
  static class AddNoteTool {
    public String front;
    public String back;
  }
```

- [ ] **Step 2: Add the `ADD_NOTE` enum constant to `DeckChatToolType`**

In `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatToolType.java`, change the enum to:

```java
public enum DeckChatToolType {
  DRAFT_NOTE(DeckChatTools.DraftNoteTool.class),
  ADD_NOTE(DeckChatTools.AddNoteTool.class);
  private final Class<?> parserClass;
```

No other changes — `fromToolName` stays as-is.

- [ ] **Step 3: Review diff**

Run: `git diff`
Expected: only the two named files changed.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/DeckChatTools.java src/main/java/com/felixkroemer/smort/domain/chat/DeckChatToolType.java
git commit -m "feat: add ADD_NOTE deck chat tool type"
```

---

### Task 2: `DraftNoteRepository.deleteInTx`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DraftNoteRepository.java`

**Interfaces:**
- Produces: `DraftNoteRepository.deleteInTx(TransactWriteItemsEnhancedRequest.Builder txBuilder, UUID deckId)` — adds a draft-note delete to the transaction.

- [ ] **Step 1: Add `deleteInTx`**

In `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DraftNoteRepository.java`, add below `saveInTx`:

```java
  public void deleteInTx(TransactWriteItemsEnhancedRequest.Builder txBuilder, UUID deckId) {
    txBuilder.addDeleteItem(
        draftNoteTable,
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(DraftNoteKeys.draftNoteSk())
            .build());
  }
```

`TransactWriteItemsEnhancedRequest`, `Key`, `DeckKeys`, `DraftNoteKeys`, and `UUID` are already imported.

- [ ] **Step 2: Review diff**

Run: `git diff`
Expected: only `DraftNoteRepository.java` changed.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DraftNoteRepository.java
git commit -m "feat: add deleteInTx to draft note repository"
```

---

### Task 3: `DeckService.storeDraftNote`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`

**Interfaces:**
- Consumes: `DeckChatToolType.ADD_NOTE` (Task 1), `DraftNoteRepository.deleteInTx` (Task 2), plus existing `draftNoteRepository.findDraftNote`, `noteEntityMapper.toNoteEntity(UUID deckId, UUID noteId, NoteSchema)`, `deckRepository.saveNoteInTx`, `chatRepository.saveInTx`, `ChatMessageEntity.toolCall`.
- Produces: `DeckService.storeDraftNote(UUID deckId) -> List<ChatMessageEntity>`.

- [ ] **Step 1: Add imports and the `enhancedClient` field**

In `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`:

Add with the other `import` statements:

```java
import com.felixkroemer.smort.domain.chat.DeckChatToolType;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
```

(`ChatMessageEntity` may already be imported — check; add only if missing. `ChatRepository`, `NoteSchema`, `NoteEntityMapper`, `DeckKeys`, `List`, `Map`, `Optional`, `UUID`, `NotFoundException` are already imported.)

Add with the other `import` statements:

```java
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
```

Add the field with the other `private final` dependencies (constructor is Lombok-generated):

```java
  private final DynamoDbEnhancedClient enhancedClient;
```

- [ ] **Step 2: Add the `storeDraftNote` method**

Add at the end of the class (after `clearDraftNote`):

```java
  public List<ChatMessageEntity> storeDraftNote(UUID deckId) {
    var draft =
        draftNoteRepository
            .findDraftNote(deckId)
            .orElseThrow(
                () -> new NotFoundException("Could not find draft note. deckId={}", deckId));

    var note =
        noteEntityMapper.toNoteEntity(
            deckId, UUID.randomUUID(), new NoteSchema(draft.getFront(), draft.getBack()));

    var addNoteMessageEntity =
        ChatMessageEntity.toolCall(
            DeckKeys.deckPk(deckId),
            deckId,
            Optional.empty(),
            UUID.randomUUID().toString(),
            Optional.empty(),
            UUID.randomUUID().toString(),
            DeckChatToolType.ADD_NOTE.name(),
            Optional.empty(),
            true,
            Map.of("front", draft.getFront(), "back", draft.getBack()));

    var txBuilder = TransactWriteItemsEnhancedRequest.builder();
    deckRepository.saveNoteInTx(txBuilder, note);
    draftNoteRepository.deleteInTx(txBuilder, deckId);
    chatRepository.saveInTx(txBuilder, addNoteMessageEntity);
    enhancedClient.transactWriteItems(txBuilder.build());

    return List.of(addNoteMessageEntity);
  }
```

- [ ] **Step 3: Review diff**

Run: `git diff`
Expected: only `DeckService.java` changed; one new field, one new method, new imports.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: add storeDraftNote to deck service"
```

---

### Task 4: `DeckController` endpoint

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`

**Interfaces:**
- Consumes: `DeckService.storeDraftNote(UUID deckId) -> List<ChatMessageEntity>` (Task 3), existing `chatMessageRestMapper.toChatMessageResponse(List<ChatMessageEntity>)`.
- Produces: `POST /decks/{deckId}/draft-note/store` returning `List<ChatMessageResponse>`.

- [ ] **Step 1: Add the endpoint**

In `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`, add after the existing `clearDraftNote` method:

```java
  @PostMapping("/{deckId}/draft-note/store")
  public List<ChatMessageResponse> storeDraftNote(@PathVariable("deckId") UUID deckId) {
    var chatMessages = deckService.storeDraftNote(deckId);
    return chatMessageRestMapper.toChatMessageResponse(chatMessages);
  }
```

`PostMapping`, `PathVariable`, `ChatMessageResponse`, `List`, `UUID`, `deckService`, and `chatMessageRestMapper` are already imported/injected.

- [ ] **Step 2: Review diff**

Run: `git diff`
Expected: only `DeckController.java` changed; one new endpoint method.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add confirm-and-add draft note endpoint"
```

---

## Post-Plan Verification (human)

- Compile and test (`./mvnw compile`, `./mvnw test`) — skipped by the implementing subagent per AGENTS.md; the human owns this.
- Manual sanity: in deck chat, draft a note, then `POST /decks/{deckId}/draft-note/store`. Expect: the ADD_NOTE chat message returned, draft gone (`GET /decks/{deckId}/draft-note` -> 404), note present in `GET /decks/{deckId}/notes`, and the ADD_NOTE event visible in the chat history and future user-action context.
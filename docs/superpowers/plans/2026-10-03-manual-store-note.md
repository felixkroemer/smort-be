# Manual Store Note Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add endpoints that let a user manually save a note's `front` and `back`, persisting the note and appending a user-initiated `STORE_NOTE` message to the note's chat history.

**Architecture:** Two REST endpoints — `PATCH /decks/{deckId}/notes/{noteId}` and `PATCH /analysis/{analysisId}/notes/{noteId}` — share an `UpdateNoteRequest(front, back)` body. Each service resolves the note and, in one DynamoDB `TransactWriteItems`, puts the updated note plus a user-initiated `STORE_NOTE` tool-call chat message. A new `ChatOrchestrationService.storeNote` builds that message without any LLM call and reuses the existing `applyToolEffect` handler mechanism.

**Tech Stack:** Java 25, Spring Boot 4 (WebMVC), AWS SDK DynamoDB Enhanced Client, MapStruct, Lombok.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-03-manual-store-note-design.md`.
- No tests are written unless explicitly requested (AGENTS.md). This plan contains no test steps.
- **Do not run or debug the build** (`./mvnw compile`, `./mvnw test`, etc.) — the human owns compilation and verifies it later. Skip all compile/test verification steps and note in reports that compilation was skipped per this instruction.
- Work happens in the `feat/manual-store-note` worktree (`git worktree` at `.worktrees/manual-store-note`); never work on main.
- Commit all work to the feature branch; never merge to main yourself.
- Follow existing code style: 2-space indent, Lombok `@RequiredArgsConstructor`, records for DTOs.
- `lastFormattedAt` is left unchanged by a manual save.

---

### Task 1: `UpdateNoteRequest` DTO

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/note/dto/UpdateNoteRequest.java`

**Interfaces:**
- Produces: `com.felixkroemer.smort.application.note.dto.UpdateNoteRequest`, a record with `String front()` and `String back()` — consumed by `DeckController.updateNote` (Task 5) and `AnalysisController.updateNote` (Task 6).

- [ ] **Step 1: Create the DTO**

```java
package com.felixkroemer.smort.application.note.dto;

public record UpdateNoteRequest(String front, String back) {}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/note/dto/UpdateNoteRequest.java
git commit -m "feat: add update note request DTO"
```

---

### Task 2: `ChatOrchestrationService.storeNote`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java`
  - Add imports `java.time.Instant` and `java.util.UUID`
  - Add the `storeNote` method after the second `formatNote` overload (after line 72)

**Interfaces:**
- Consumes:
  - `ChatMessageEntity.toolCall(...)` (exists)
  - `ChatMessageMeta(String, Optional<String>, Instant)` (exists)
  - `StoreNoteToolChatMessage(String callId, String front, String back, ChatMessageMeta meta)` (exists)
  - `NoteChatToolType.STORE_NOTE.name()` (exists)
  - `ChatRepository.saveInTx(TransactWriteItemsEnhancedRequest.Builder, ChatMessageEntity)` (exists)
  - `applyToolEffect(TransactWriteItemsEnhancedRequest.Builder, ChatMessage, Map<...>)` (private, exists)
- Produces: `public <T> List<ChatMessageEntity> storeNote(String pk, T entityId, String front, String back, Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers)` — consumed by `NoteService.updateNote` (Task 3) and `AnkiNoteService.updateNote` (Task 4).

- [ ] **Step 1: Add the imports**

Add with the other `java.*` imports:

```java
import java.time.Instant;
import java.util.UUID;
```

- [ ] **Step 2: Add the `storeNote` method**

Add after the second `formatNote` overload (the one ending at line 72 with `return List.of(formatChatMessageEntity); }`):

```java
  public <T> List<ChatMessageEntity> storeNote(
      String pk,
      T entityId,
      String front,
      String back,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
    var meta = new ChatMessageMeta(UUID.randomUUID().toString(), Optional.empty(), Instant.now());
    var storeNoteToolChatMessage = new StoreNoteToolChatMessage("", front, back, meta);

    var storeNoteChatMessageEntity =
        ChatMessageEntity.toolCall(
            pk,
            entityId,
            Optional.empty(),
            meta.responseId(),
            Optional.empty(),
            storeNoteToolChatMessage.callId(),
            NoteChatToolType.STORE_NOTE.name(),
            Optional.empty(),
            true,
            Map.of("front", front, "back", back));

    var txBuilder = TransactWriteItemsEnhancedRequest.builder();
    chatRepository.saveInTx(txBuilder, storeNoteChatMessageEntity);
    applyToolEffect(txBuilder, storeNoteToolChatMessage, toolHandlers);
    enhancedClient.transactWriteItems(txBuilder.build());

    return List.of(storeNoteChatMessageEntity);
  }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java
git commit -m "feat: add manual store note orchestration"
```

---

### Task 3: `NoteService.updateNote`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java`
  - Add import `com.felixkroemer.smort.common.exception.SmortException`
  - Add the `updateNote` method after `formatNote` (after line 69)

**Interfaces:**
- Consumes:
  - `DeckRepository.findNoteByDeckIdAndNoteId(UUID, UUID)` (exists)
  - `DeckRepository.saveNoteInTx(TransactWriteItemsEnhancedRequest.Builder, NoteEntity)` (exists)
  - `ChatOrchestrationService.storeNote(...)` (Task 2)
  - `DeckKeys.deckPk(UUID)` (exists)
- Produces: `public NoteEntity updateNote(UUID deckId, UUID noteId, String front, String back)` — consumed by `DeckController.updateNote` (Task 5).

- [ ] **Step 1: Add the import**

```java
import com.felixkroemer.smort.common.exception.SmortException;
```

- [ ] **Step 2: Add the `updateNote` method**

Add after the `formatNote` method (ends at line 69):

```java
  public NoteEntity updateNote(UUID deckId, UUID noteId, String front, String back) {
    if (front == null || back == null) {
      throw new SmortException("Note front and back must not be null. noteId={}", noteId);
    }

    var note =
        deckRepository
            .findNoteByDeckIdAndNoteId(deckId, noteId)
            .orElseThrow(() -> new NotFoundException("Note not found. id={}", noteId));

    Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatMessage.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatMessage) toolCall;
              note.setFront(m.front());
              note.setBack(m.back());
              deckRepository.saveNoteInTx(tx, note);
            });

    chatOrchestrationService.storeNote(
        DeckKeys.deckPk(deckId), noteId, front, back, toolHandlers);

    return note;
  }
```

All other types used (`NotFoundException`, `Map`, `ChatMessage`, `ToolCallHandler`, `StoreNoteToolChatMessage`, `DeckKeys`) are already imported via `com.felixkroemer.smort.domain.chat.*` and existing imports.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java
git commit -m "feat: add manual note update service method"
```

---

### Task 4: `AnkiNoteService.updateNote`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java`
  - Add imports `com.felixkroemer.smort.common.exception.NotFoundException`, `com.felixkroemer.smort.common.exception.SmortException`, `jakarta.persistence.NoResultException`
  - Add the `updateNote` method after `formatNote` (after line 92)

**Interfaces:**
- Consumes:
  - `AnkiNoteRepository.findNoteByAnalysisIdAndNoteId(UUID, Long)` (exists; throws `NoResultException` when absent)
  - `DerivedNoteEntity(UUID analysisId, Long noteId, String front, String back)` constructor (exists)
  - `DerivedNoteRepository.saveInTx(TransactWriteItemsEnhancedRequest.Builder, DerivedNoteEntity)` (exists)
  - `ChatOrchestrationService.storeNote(...)` (Task 2)
  - `AnalysisKeys.analysisPk(UUID)` (exists)
- Produces: `public DerivedNoteEntity updateNote(UUID analysisId, Long noteId, String front, String back)` — consumed by `AnalysisController.updateNote` (Task 6).

- [ ] **Step 1: Add the imports**

```java
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import jakarta.persistence.NoResultException;
```

- [ ] **Step 2: Add the `updateNote` method**

Add after the `formatNote` method (ends at line 92):

```java
  public DerivedNoteEntity updateNote(UUID analysisId, Long noteId, String front, String back) {
    if (front == null || back == null) {
      throw new SmortException("Note front and back must not be null. noteId={}", noteId);
    }

    try {
      ankiNoteRepository.findNoteByAnalysisIdAndNoteId(analysisId, noteId);
    } catch (NoResultException e) {
      throw new NotFoundException("Note not found. id={}", noteId);
    }

    var derivedNote =
        getDerivedNote(analysisId, noteId)
            .map(
                d -> {
                  d.setFront(front);
                  d.setBack(back);
                  return d;
                })
            .orElseGet(() -> new DerivedNoteEntity(analysisId, noteId, front, back));

    Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatMessage.class,
            (tx, toolCall) -> derivedNoteRepository.saveInTx(tx, derivedNote));

    chatOrchestrationService.storeNote(
        AnalysisKeys.analysisPk(analysisId), noteId, front, back, toolHandlers);

    return derivedNote;
  }
```

`DerivedNoteEntity` is already imported via `com.felixkroemer.smort.infrastructure.dynamodb.anki.*`; `Map`, `ChatMessage`, `ToolCallHandler`, `StoreNoteToolChatMessage`, `AnalysisKeys`, `UUID` are already imported. The new `DerivedNoteEntity(...)` constructor leaves `lastFormattedAt` at its field default `Optional.empty()`, and an existing derived note keeps its current value — matching the spec.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java
git commit -m "feat: add manual derived note update service method"
```

---

### Task 5: `DeckController` endpoint

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`
  - Add import `com.felixkroemer.smort.application.note.dto.UpdateNoteRequest`
  - Add the endpoint method after `formatNote` (after line 102)

**Interfaces:**
- Consumes: `UpdateNoteRequest` (Task 1), `NoteService.updateNote(UUID, UUID, String, String)` (Task 3), `NoteRestMapper.toNoteResponse(NoteEntity)` (exists).
- Produces: endpoint `PATCH /decks/{deckId}/notes/{noteId}` returning `NoteResponse`.

- [ ] **Step 1: Add the import**

```java
import com.felixkroemer.smort.application.note.dto.UpdateNoteRequest;
```

- [ ] **Step 2: Add the endpoint**

Add after the `formatNote` method (after line 102):

```java
  @PatchMapping("/{deckId}/notes/{noteId}")
  public NoteResponse updateNote(
      @PathVariable("deckId") UUID deckId,
      @PathVariable("noteId") UUID noteId,
      @RequestBody UpdateNoteRequest request) {
    var note = noteService.updateNote(deckId, noteId, request.front(), request.back());
    return noteRestMapper.toNoteResponse(note);
  }
```

`@PatchMapping`, `@PathVariable`, `@RequestBody` come from the existing `import org.springframework.web.bind.annotation.*`; `NoteResponse`, `UUID`, `noteService`, and `noteRestMapper` are already present.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add manual note update endpoint"
```

---

### Task 6: `AnalysisController` endpoint

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java`
  - Add import `com.felixkroemer.smort.application.note.dto.UpdateNoteRequest`
  - Add the endpoint method after `formatNote` (after line 190)

**Interfaces:**
- Consumes: `UpdateNoteRequest` (Task 1), `AnkiNoteService.updateNote(UUID, Long, String, String)` (Task 4), `AnkiNoteRestMapper.toDerivedNoteResponse(DerivedNoteEntity)` (exists).
- Produces: endpoint `PATCH /analysis/{analysisId}/notes/{noteId}` returning `DerivedNoteResponse`.

- [ ] **Step 1: Add the import**

```java
import com.felixkroemer.smort.application.note.dto.UpdateNoteRequest;
```

- [ ] **Step 2: Add the endpoint**

Add after the `formatNote` method (after line 190):

```java
  @PatchMapping("/{analysisId}/notes/{noteId}")
  public DerivedNoteResponse updateNote(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("noteId") Long noteId,
      @RequestBody UpdateNoteRequest request) {
    var derivedNote =
        ankiNoteService.updateNote(analysisId, noteId, request.front(), request.back());
    return ankiNoteRestMapper.toDerivedNoteResponse(derivedNote);
  }
```

`@PatchMapping`, `@PathVariable`, `@RequestBody` come from the existing `import org.springframework.web.bind.annotation.*`; `DerivedNoteResponse` comes from `import com.felixkroemer.smort.application.anki.dto.*`; `UUID`, `ankiNoteService`, and `ankiNoteRestMapper` are already present.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java
git commit -m "feat: add manual derived note update endpoint"
```

---

## Self-Review

**Spec coverage:**
- `PATCH /decks/{deckId}/notes/{noteId}` → updated `NoteResponse` → Task 1 + Task 5.
- `PATCH /analysis/{analysisId}/notes/{noteId}` → updated `DerivedNoteResponse` (upsert) → Task 1 + Task 4 + Task 6.
- Shared `UpdateNoteRequest(front, back)` in `application/note/dto` → Task 1.
- User-initiated `STORE_NOTE` TOOL_CALL message, synthetic `UUID` responseId, `callId=""`, empty message/previousResponseId, `userInitiated=true`, no LLM call → Task 2.
- Note put + chat-message put in one transaction → Task 2 (`storeNote`) reused by Tasks 3 and 4.
- `lastFormattedAt` unchanged; new derived note starts empty → Tasks 3 and 4.
- 404 on missing note; 400 on null front/back → Tasks 3 and 4.
- Analysis base-note existence translated from `NoResultException` to `NotFoundException` → Task 4.
- No Terraform changes; no tests; compilation skipped → Global Constraints.

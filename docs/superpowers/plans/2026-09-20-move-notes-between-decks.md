# Move Notes Between Decks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an endpoint that moves a list of notes from a source deck to a target deck, all-or-nothing.

**Architecture:** One REST endpoint `POST /decks/{deckId}/notes/move` (source deck in path, target deck in body). The service loads the notes from the source deck partition, validates every id, and re-keys each note into the target deck's partition inside a single DynamoDB `TransactWriteItems` (delete old key + put new key per note). Note chat history in the source partition is deleted after the transaction commits.

**Tech Stack:** Java 25, Spring Boot 4 (WebMVC), AWS SDK DynamoDB Enhanced Client, MapStruct, Lombok.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-20-move-notes-between-decks-design.md`.
- No tests are written unless explicitly requested (AGENTS.md). This plan contains no test steps.
- **Do not run or debug the build** (`./mvnw compile`, `./mvnw test`, etc.) — the human owns compilation and verifies it later. Skip all compile/test verification steps and note in reports that compilation was skipped per this instruction.
- Work happens in the `feat/move-notes-between-decks` worktree (`git worktree` at `.worktrees/move-notes-between-decks`); never work on main.
- Commit all work to the feature branch; never merge to main yourself.
- Follow existing code style: 2-space indent, Lombok `@RequiredArgsConstructor`, records for DTOs.

---

### Task 1: `MoveNotesRequest` DTO

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/deck/dto/MoveNotesRequest.java`

**Interfaces:**
- Produces: `com.felixkroemer.smort.application.deck.dto.MoveNotesRequest`, a record with `List<UUID> noteIds()` and `UUID targetDeckId()` — consumed by `DeckController.moveNotes` (Task 4).

- [ ] **Step 1: Create the DTO**

```java
package com.felixkroemer.smort.application.deck.dto;

import java.util.List;
import java.util.UUID;

public record MoveNotesRequest(List<UUID> noteIds, UUID targetDeckId) {}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/dto/MoveNotesRequest.java
git commit -m "feat: add move notes request DTO"
```

---

### Task 2: `DeckRepository.deleteNoteInTx`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckRepository.java` (add a method after `saveNoteInTx`, around line 48)

**Interfaces:**
- Consumes: `NoteSortKeys.noteSk(UUID)` and `DeckKeys.deckPk(UUID)` (both already exist).
- Produces: `void deleteNoteInTx(TransactWriteItemsEnhancedRequest.Builder txBuilder, UUID deckId, UUID noteId)` — consumed by `DeckService.moveNotes` (Task 3).

- [ ] **Step 1: Add the transactional delete method**

```java
  public void deleteNoteInTx(
      TransactWriteItemsEnhancedRequest.Builder txBuilder, UUID deckId, UUID noteId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(NoteSortKeys.noteSk(noteId))
            .build();
    txBuilder.addDeleteItem(noteTable, key);
  }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckRepository.java
git commit -m "feat: add transactional note delete to deck repository"
```

---

### Task 3: `DeckService.moveNotes`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`
  - Add `import lombok.extern.slf4j.Slf4j;`
  - Add `@Slf4j` to the class annotations (line 49-51)
  - Add the `moveNotes` method after `deleteNote` (after line 223)

**Interfaces:**
- Consumes:
  - `DeckRepository.findNotesByDeckId(UUID)` (exists)
  - `DeckRepository.saveNoteInTx(TransactWriteItemsEnhancedRequest.Builder, NoteEntity)` (exists)
  - `DeckRepository.deleteNoteInTx(...)` (Task 2)
  - `ChatRepository.deleteChat(String pk, T entityId)` (exists; called with `DeckKeys.deckPk(sourceDeckId)` and a `UUID`)
  - `getMeta(UUID)` (private method, exists, throws `NotFoundException`)
  - `DeckKeys.deckPk(UUID)` (exists)
- Produces: `public void moveNotes(UUID sourceDeckId, List<UUID> noteIds, UUID targetDeckId)` — consumed by `DeckController.moveNotes` (Task 4).

- [ ] **Step 1: Add `@Slf4j` annotation and import**

Add to the class declaration block:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DeckService {
```

And add the import with the other `lombok` imports:

```java
import lombok.extern.slf4j.Slf4j;
```

- [ ] **Step 2: Add the `moveNotes` method**

Add after the `deleteNote` method (ends around line 223):

```java
  public void moveNotes(UUID sourceDeckId, List<UUID> noteIds, UUID targetDeckId) {
    if (sourceDeckId.equals(targetDeckId)) {
      throw new SmortException(
          "Cannot move notes within the same deck. deckId={}", sourceDeckId);
    }
    getMeta(sourceDeckId);
    getMeta(targetDeckId);

    var uniqueNoteIds = noteIds.stream().distinct().toList();
    var notesByNoteId =
        deckRepository.findNotesByDeckId(sourceDeckId).stream()
            .collect(Collectors.toMap(NoteEntity::getId, Function.identity()));

    var notes =
        uniqueNoteIds.stream()
            .map(
                noteId ->
                    Optional.ofNullable(notesByNoteId.get(noteId))
                        .orElseThrow(
                            () ->
                                new NotFoundException(
                                    "Could not find note in deck. deckId={}, noteId={}",
                                    sourceDeckId,
                                    noteId)))
            .toList();

    var txBuilder = TransactWriteItemsEnhancedRequest.builder();
    notes.forEach(
        note -> {
          deckRepository.deleteNoteInTx(txBuilder, sourceDeckId, note.getId());
          note.setPk(DeckKeys.deckPk(targetDeckId));
          note.setDeckId(targetDeckId);
          deckRepository.saveNoteInTx(txBuilder, note);
        });
    enhancedClient.transactWriteItems(txBuilder.build());

    notes.forEach(
        note -> chatRepository.deleteChat(DeckKeys.deckPk(sourceDeckId), note.getId()));

    log.info(
        "Moved notes. sourceDeckId={}, targetDeckId={}, noteIds={}",
        sourceDeckId,
        targetDeckId,
        uniqueNoteIds);
  }
```

All imports used (`SmortException`, `NotFoundException`, `Function`, `Optional`, `Collectors`, `DeckKeys`, `TransactWriteItemsEnhancedRequest`, `NoteEntity`) are already present in the file.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: add move notes service method"
```

---

### Task 4: `DeckController` endpoint

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`
  - Add `import com.felixkroemer.smort.application.deck.dto.MoveNotesRequest;`
  - Add the endpoint method after `deleteNotes` (after line 228)

**Interfaces:**
- Consumes: `MoveNotesRequest` (Task 1), `DeckService.moveNotes(UUID, List<UUID>, UUID)` (Task 3).
- Produces: endpoint `POST /decks/{deckId}/notes/move` returning `204 No Content`.

- [ ] **Step 1: Add the import**

```java
import com.felixkroemer.smort.application.deck.dto.MoveNotesRequest;
```

- [ ] **Step 2: Add the endpoint**

Add after the `deleteNotes` method (after line 228):

```java
  @PostMapping("/{deckId}/notes/move")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void moveNotes(
      @PathVariable("deckId") UUID deckId, @RequestBody MoveNotesRequest request) {
    deckService.moveNotes(deckId, request.noteIds(), request.targetDeckId());
  }
```

`@PostMapping`, `@PathVariable`, `@RequestBody`, `@ResponseStatus` come from the existing `import org.springframework.web.bind.annotation.*`; `HttpStatus` and `UUID` are already imported.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add move notes endpoint"
```

---

## Self-Review

**Spec coverage:**
- Endpoint `POST /decks/{deckId}/notes/move` with `MoveNotesRequest(noteIds, targetDeckId)`, 204 → Task 1 + Task 4.
- `DeckService.moveNotes` next to `deleteNotes` → Task 3.
- `DeckRepository.deleteNoteInTx`, reuse `saveNoteInTx` → Task 2.
- All-or-nothing single transaction, validation (source != target via `SmortException`, deck existence via `getMeta`, missing note via `NotFoundException`), dedupe, empty list no-op, chat deletion post-commit → Task 3.
- No tests, compilation skipped → Global Constraints.
# Delete Multiple Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `DELETE /decks/{deckId}/notes` endpoint that deletes several notes of a deck in one call and also clears each note's chat history, unifying note deletion so the single-note delete clears chat too.

**Architecture:** A new `DeleteNotesRequest` record carries the note ids. `DeckService.deleteNotes(deckId, noteIds)` clears each note's chat via the existing `ChatRepository.deleteChat` and then batch-deletes the note items through a new `DeckRepository` method that reuses the `batchWriteItem` batching pattern from `deleteDeckNotes`. `deleteNote` delegates to `deleteNotes`, so single and bulk deletes share one code path.

**Tech Stack:** Java 17, Spring Web (`@RestController`, `@DeleteMapping`, request records), AWS SDK DynamoDB Enhanced Client (`DynamoDbTable`, `batchWriteItem`), Lombok, Maven.

## Global Constraints

- Do not write tests (per AGENTS.md — tests only when explicitly requested).
- Do not run or debug the build (`./mvnw compile`, `./mvnw test`); compilation is owned by the human and skipped. Note this in the completion report.
- Delete of non-existent note ids is a silent idempotent no-op (DynamoDB `deleteItem` semantics) — the endpoint returns `204 No Content` regardless.
- Note deletion and chat deletion are not wrapped in a transaction; consistent with the existing single-note delete.
- Commit each task's changes to the feature branch `feat/delete-multiple-notes`.

---

### Task 1: Add `DeleteNotesRequest` record DTO

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/deck/dto/DeleteNotesRequest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `record DeleteNotesRequest(List<UUID> noteIds)` — consumed by `DeckController` in Task 4.

- [ ] **Step 1: Create the record**

```java
package com.felixkroemer.smort.application.deck.dto;

import java.util.List;
import java.util.UUID;

public record DeleteNotesRequest(List<UUID> noteIds) {}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/dto/DeleteNotesRequest.java
git commit -m "feat: add DeleteNotesRequest DTO"
```

---

### Task 2: Add `DeckRepository.deleteNotesByDeckIdAndNoteIds`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckRepository.java`

**Interfaces:**
- Consumes: existing `DeckKeys.deckPk(UUID deckId)`, `NoteSortKeys.noteSk(UUID noteId)` (both already imported), existing `dynamoDbEnhancedClient` field, existing `WriteBatch`, `BatchWriteItemEnhancedRequest`, `Key`, `IntStream` imports (all used by `deleteDeckNotes` at `DeckRepository.java:135-172`).
- Produces: `void deleteNotesByDeckIdAndNoteIds(UUID deckId, List<UUID> noteIds)` — consumed by `DeckService.deleteNotes` in Task 3.

- [ ] **Step 1: Add the batch delete method**

Place it directly after `deleteNoteByDeckIdAndNoteId` (`DeckRepository.java:108-115`):

```java
public void deleteNotesByDeckIdAndNoteIds(UUID deckId, List<UUID> noteIds) {
  var uniqueNoteIds = noteIds.stream().distinct().toList();

  IntStream.range(0, (uniqueNoteIds.size() + 24) / 25)
      .mapToObj(i -> uniqueNoteIds.subList(i * 25, Math.min((i + 1) * 25, uniqueNoteIds.size())))
      .forEach(
          batch -> {
            WriteBatch.Builder<NoteEntity> writeBatch =
                WriteBatch.builder(NoteEntity.class).mappedTableResource(noteTable);
            batch.forEach(
                noteId ->
                    writeBatch.addDeleteItem(
                        Key.builder()
                            .partitionValue(DeckKeys.deckPk(deckId))
                            .sortValue(NoteSortKeys.noteSk(noteId))
                            .build()));
            dynamoDbEnhancedClient.batchWriteItem(
                BatchWriteItemEnhancedRequest.builder().writeBatches(writeBatch.build()).build());
          });

  log.info("Deleted deck notes. deckId={}, noteIds={}", deckId, uniqueNoteIds);
}
```

Note: when `noteIds` is empty, `uniqueNoteIds.size() + 24) / 25` is `0`, so `IntStream.range(0, 0)` produces an empty stream and no write happens. When `uniqueNoteIds` is empty, `subList` is never called (no elements).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckRepository.java
git commit -m "feat: add batched note deletion to DeckRepository"
```

---

### Task 3: Add `DeckService.deleteNotes` and delegate `deleteNote` to it

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`

**Interfaces:**
- Consumes: `DeckRepository.deleteNotesByDeckIdAndNoteIds(UUID, List<UUID>)` (Task 2), existing `ChatRepository.deleteChat(String pk, T entityId)` (`ChatRepository.java:76-82`, clears `CHAT#C#<noteId>#` and `CHAT#U#<noteId>#` keys), existing `DeckKeys.deckPk(UUID deckId)` (imported at `DeckService.java:33`), existing `chatRepository` field (`DeckService.java:59`).
- Produces: `void deleteNotes(UUID deckId, List<UUID> noteIds)` and the changed single-note `void deleteNote(UUID deckId, UUID noteId)` — consumed by `DeckController` in Task 4.

- [ ] **Step 1: Add `deleteNotes` and change `deleteNote`**

Replace the body of `deleteNote` (`DeckService.java:213-215`) with:

```java
public void deleteNotes(UUID deckId, List<UUID> noteIds) {
  var pk = DeckKeys.deckPk(deckId);
  noteIds.forEach(noteId -> chatRepository.deleteChat(pk, noteId));
  deckRepository.deleteNotesByDeckIdAndNoteIds(deckId, noteIds);
}

public void deleteNote(UUID deckId, UUID noteId) {
  deleteNotes(deckId, List.of(noteId));
}
```

`List` is already imported (`DeckService.java:35`). This clears the note's chat for both single and bulk deletes, then batch-deletes the note items.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: add DeckService.deleteNotes and clear chat on note delete"
```

---

### Task 4: Add the bulk-delete endpoint to `DeckController`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`

**Interfaces:**
- Consumes: `DeleteNotesRequest` (Task 1), `DeckService.deleteNotes(UUID, List<UUID>)` (Task 3).
- Produces: `DELETE /decks/{deckId}/notes` returning `204 No Content`.

- [ ] **Step 1: Add the import**

Add next to the other `application.deck.dto` imports (`DeckController.java:8-13`):

```java
import com.felixkroemer.smort.application.deck.dto.DeleteNotesRequest;
```

- [ ] **Step 2: Add the endpoint**

Place it directly before the single-note delete mapping (`DeckController.java:178`):

```java
@DeleteMapping("/{deckId}/notes")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteNotes(
    @PathVariable("deckId") UUID deckId, @RequestBody DeleteNotesRequest request) {
  deckService.deleteNotes(deckId, request.noteIds());
}
```

`HttpStatus`, `ResponseStatus`, `PathVariable`, `RequestBody`, and `DeleteMapping` are already available via the imports at `DeckController.java:26-27` (`org.springframework.http.HttpStatus` and `org.springframework.web.bind.annotation.*`).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add DELETE /decks/{deckId}/notes bulk endpoint"
```
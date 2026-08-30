# Clear Draft Note Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a REST endpoint to clear a deck's draft note (delete the single per-deck `DraftNoteEntity`), returning 204, idempotently.

**Architecture:** Reuse the existing `DraftNoteRepository.delete(UUID)` (already used by `DeckCron`) behind a thin `DeckService.clearDraftNote(UUID)` method and a `DELETE /{deckId}/draft-note` controller endpoint. DynamoDB `deleteItem` on an absent key is a no-op, so idempotency is free.

**Tech Stack:** Java (Spring Boot), DynamoDB Enhanced Client

## Global Constraints

- Branch: `feat/clear-draft-note` (already created and checked out)
- Do NOT run build/compile commands (`./mvnw compile`, `./mvnw test`, etc.). The human owns compilation and verifies it later.
- Write NO tests. Tests are only written when explicitly requested. Each task ends with a commit instead of a test cycle.
- Follow existing code conventions (Lombok, Spring `@RestController`, `@RequiredArgsConstructor`).
- Chat history is NOT touched by this feature.

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `domain/deck/DeckService.java` | Modify | Add `clearDraftNote(UUID deckId)` |
| `application/deck/DeckController.java` | Modify | Add `DELETE /{deckId}/draft-note` returning 204 |

---

### Task 1: Clear draft note service method and endpoint

Adds `DeckService.clearDraftNote` and the `DELETE /{deckId}/draft-note` endpoint.

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`

**Interfaces:**
- Consumes: `DraftNoteRepository.delete(UUID deckId)` (exists; used by `DeckCron`), `DraftNoteRepository` field already on `DeckService`
- Produces: `DeckService.clearDraftNote(UUID deckId)` (void); `DELETE /{deckId}/draft-note` returning 204 No Content

- [ ] **Step 1: Add `clearDraftNote` to `DeckService.java`**

Add this method right after the existing `getDraftNote` method (at the end of the class, after line 197):

```java
  public void clearDraftNote(UUID deckId) {
    draftNoteRepository.delete(deckId);
  }
```

`draftNoteRepository` is already a field of `DeckService` and `draftNoteRepository.delete(deckId)` already exists in `DraftNoteRepository.java:33-40`. No new imports needed.

- [ ] **Step 2: Add the DELETE endpoint to `DeckController.java`**

Add this endpoint right after the existing `GET /{deckId}/draft-note` (line 114):

```java
  @DeleteMapping("/{deckId}/draft-note")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearDraftNote(@PathVariable("deckId") UUID deckId) {
    deckService.clearDraftNote(deckId);
  }
```

`@DeleteMapping`, `@ResponseStatus`, `HttpStatus`, and `@PathVariable` are all already used elsewhere in this controller (e.g. `startBulkFormat` at line 74-77 uses `@ResponseStatus(HttpStatus.ACCEPTED)`; `deleteDeck` at line 129 uses `@DeleteMapping`), so no new imports are needed. `DraftNoteResponse` remains used by the GET endpoint.

- [ ] **Step 3: Verify your work**

Read both edited files back to confirm: the service method calls `draftNoteRepository.delete(deckId)` and the endpoint is a `void` method annotated `@ResponseStatus(HttpStatus.NO_CONTENT)` under `DELETE /{deckId}/draft-note`. Do NOT compile.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java \
       src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add clear draft note endpoint"
```

---

## Notes for the Implementer

- Compilation is intentionally skipped per AGENTS.md; the human owns build verification.
- The clear operation is idempotent: `DynamoDbTable.deleteItem` on a key that does not exist is a no-op, so clearing an already-cleared draft returns 204.
- Do not touch chat history, the read endpoint, or the draft tool — out of scope.
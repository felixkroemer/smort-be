# Clear Draft Note Design

## Goal

Expose a REST endpoint that clears a deck's draft note. Clearing deletes the single
per-deck `DraftNoteEntity`; it does not touch the chat history.

## Scope

In scope:
- `DeckService.clearDraftNote(UUID deckId)` service method.
- `DELETE /{deckId}/draft-note` controller endpoint returning 204 No Content.
- Idempotent behavior: clearing when no draft exists succeeds.

Out of scope:
- Touching chat history (prior draft tool-call messages stay as the conversation record).
- Any new DTO, mapper, or repository code.
- Any change to the draft tool, the deck chat, or the read endpoint.

## Approach

Reuse the existing `DraftNoteRepository.delete(UUID deckId)` (already used by `DeckCron`)
behind a thin service method and a RESTful DELETE endpoint. DynamoDB `deleteItem` on a
non-existent key is a no-op, so idempotency comes for free.

## Changes

### 1. `DeckService.clearDraftNote(UUID deckId)`

```java
public void clearDraftNote(UUID deckId) {
  draftNoteRepository.delete(deckId);
}
```

`draftNoteRepository` is already a field of `DeckService` (added by the draft note
feature). No new imports.

### 2. `DeckController` endpoint

```java
@DeleteMapping("/{deckId}/draft-note")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void clearDraftNote(@PathVariable("deckId") UUID deckId) {
  deckService.clearDraftNote(deckId);
}
```

`DeleteMapping`, `ResponseStatus`, `HttpStatus`, and `PathVariable` are already
imported by `DeckController` (used by the existing `deleteDeck`/`deleteNote`
endpoints).

## Error Handling

- No error cases. Deleting an absent draft is a silent success (204), per the
  idempotent no-op decision.

## Testing

No tests. Per AGENTS.md, tests are only written when explicitly requested. Compilation
is owned by the human and verified later.

## Files

| File | Change |
|------|--------|
| `domain/deck/DeckService.java` | Add `clearDraftNote(UUID deckId)` |
| `application/deck/DeckController.java` | Add `DELETE /{deckId}/draft-note` |
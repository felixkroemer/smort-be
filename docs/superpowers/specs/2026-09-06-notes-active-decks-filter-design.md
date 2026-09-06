# All-Notes Endpoint: Filter to Active Decks Only

## Goal

The `GET /notes` endpoint must not return notes whose deck is no longer shown. A deck can be in status `IMPORTING`, `ACTIVE`, or `MARKED_FOR_DELETION`; the frontend's deck list (`GET /decks`) only ever shows `ACTIVE` decks. Notes belonging to any other deck state must be hidden from the all-notes response.

## Design

Filter in `DeckService.getAllNotes()`: query the decks the user owns via the existing `getDecks()` method (which returns only `ACTIVE` decks), then keep only the notes whose `deckId` is in that set.

No repository changes, no method renames, no new query methods. The only production-code change is the body of `getAllNotes()`.

```java
public List<NoteEntity> getAllNotes() {
  var deckIds = getDecks().stream().map(Deck::getDeckId).collect(Collectors.toSet());
  return deckRepository.findNotesByUserId("default").stream()
      .filter(note -> deckIds.contains(note.getDeckId()))
      .toList();
}
```

## Behavior

- Notes are returned only when their deck is `ACTIVE` (i.e. appears in `getDecks()`).
- Notes from `IMPORTING` and `MARKED_FOR_DELETION` decks are excluded.
- Notes with a `null` `deckId` (rows written before the `deckId` attribute existed) are excluded, since `null` is in no deck set.
- The current user is the hardcoded dummy `"default"`; `getAllNotes()` already queries that user, and `getDecks()` also targets `"default"`.
- `GET /decks` behavior is unchanged. Per-deck `GET /decks/{deckId}/notes` is unchanged (not in scope).

## Out of Scope

- Renaming `getDecks()` or any repository methods.
- New repository query methods.
- A status filter at the DynamoDB query level.
- Changes to the per-deck notes endpoint.

## Files Changed

- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java` — body of `getAllNotes()` only.

`Deck` and `Collectors` are already imported in `DeckService` (no import changes).
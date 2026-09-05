# Design: Delete multiple notes at once

Date: 2026-09-06

## Goal

Notes can currently only be deleted one at a time via
`DELETE /decks/{deckId}/notes/{noteId}`. Add a bulk endpoint to delete several
notes of a deck in one call. As a side effect, unify note deletion so that
deleting a note (single or multi) also clears the note's chat history, which
the single-note delete currently leaves behind.

## Approach

Add `DELETE /decks/{deckId}/notes` taking a `DeleteNotesRequest` body listing
the note ids to delete. Implement a single `DeckService.deleteNotes(deckId,
noteIds)` method that:

1. clears each note's chat history via `ChatRepository.deleteChat(pk, noteId)`,
2. batch-deletes the note items via a new `DeckRepository` method that mirrors
   the existing `deleteDeckNotes` batched `batchWriteItem` pattern.

The existing single-note delete (`deleteNote`) is changed to delegate to
`deleteNotes(deckId, List.of(noteId))`, so single and bulk deletes share one
code path and both clear chat.

Like `deleteItem`, batch deletion of non-existent keys is a silent no-op, so
the endpoint is idempotent and returns `204 No Content` regardless.

## Changes

1. **`DeckController`** — add
   `@DeleteMapping("/{deckId}/notes")` returning
   `@ResponseStatus(HttpStatus.NO_CONTENT)`, delegating to
   `deckService.deleteNotes(deckId, request.noteIds())`.

2. **`DeleteNotesRequest`** (new, in `application/deck/dto`) — record
   `DeleteNotesRequest(List<UUID> noteIds)`, matching existing request-record
   conventions.

3. **`DeckService`** —
   - add `deleteNotes(UUID deckId, List<UUID> noteIds)` which clears chat per
     note and then delegates to the repository for the note-item deletion;
   - change `deleteNote(UUID deckId, UUID noteId)` to call
     `deleteNotes(deckId, List.of(noteId))`.

4. **`DeckRepository`** — add `deleteNotesByDeckIdAndNoteIds(UUID deckId,
   List<UUID> noteIds)` that builds the note keys (`DeckKeys.deckPk(deckId)` /
   `NoteSortKeys.noteSk(noteId)`), dedupes them, and deletes in batches of 25
   using `batchWriteItem`, mirroring `deleteDeckNotes`.

## Data flow

`DELETE /decks/{deckId}/notes {noteIds:[...]}` → `DeckService.deleteNotes` →
per note `ChatRepository.deleteChat(DeckKeys.deckPk(deckId), noteId)` (removes
`LLM_CHAT_MESSAGE#<noteId>#…` and `USER_CHAT_MESSAGE#<noteId>#…` keys) →
`DeckRepository.deleteNotesByDeckIdAndNoteIds` → one `batchWriteItem` per 25
notes.

## Error handling

None new. Deletion of already-missing notes is a no-op (idempotent), matching
the existing single-note delete. Note and chat deletion is not wrapped in a
transaction, consistent with the existing single-note delete.

## Testing

No new tests (per AGENTS.md, tests are only written when explicitly requested).
Compilation is owned by the human and skipped in implementation.

## Deviation note

The original request was to add a multi-note delete endpoint. During
brainstorming the human asked that the single-note delete also clear its chat
history and that multi-note delete do the same, so this spec includes that
unification.
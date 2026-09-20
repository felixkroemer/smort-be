# Move Notes Between Decks — Design

Date: 2026-09-20

## Overview

Users can move a selection of notes from one deck to another. A single
endpoint takes a list of note ids (from the source deck in the path) plus the
target deck id, and moves them all-or-nothing.

Moved notes keep their `id` and content. Their chat history is deleted (moved
notes start fresh in the target deck).

## Data model

A note is a DynamoDB item in the source deck's partition:

- `pk` = `DECK#<deckId>` (`DeckKeys.deckPk`)
- `sk` = `NOTE#<noteId>` (`NoteSortKeys.noteSk`)
- GSI `UserNoteIndex`: `userNoteIndexGsiPk` = `USER#<userId>`,
  `userNoteIndexGsiSk` = `NOTE#<noteId>`
- `deckId` (UUID), `id` (UUID), `front`, `back`, `lastFormattedAt`, `userId`

Moving a note to another deck changes its partition key and `deckId` field;
the GSI keys are user/note-based and unchanged. A DynamoDB item's key is
immutable, so the move is a `DeleteItem` (old key) plus `PutItem` (new key)
per note, executed as one transaction.

## Endpoint

```
POST /decks/{deckId}/notes/move
```

where `deckId` in the path is the **source** deck. Body:

```
MoveNotesRequest(List<UUID> noteIds, UUID targetDeckId)
```

Returns `204 No Content` on success, mirroring
`DELETE /decks/{deckId}/notes`.

## Components

- `MoveNotesRequest` (`application/deck/dto`): record with `noteIds` and
  `targetDeckId`, parallel to `DeleteNotesRequest`.
- `DeckService.moveNotes(UUID sourceDeckId, List<UUID> noteIds, UUID
  targetDeckId)`: service method next to `deleteNotes`.
- `DeckRepository.deleteNoteInTx(TransactWriteItemsEnhancedRequest.Builder
  txBuilder, UUID deckId, UUID noteId)`: new repository method; reuse existing
  `saveNoteInTx`.

## Data flow

1. Validate: `sourceDeckId != targetDeckId` (throw `SmortException`);
   dedupe `noteIds`.
2. Check both decks exist via `getMeta` (throw `NotFoundException` if not).
3. Load notes with `findNotesByDeckId(sourceDeckId)`, map by id, and verify
   every requested id is present (throw `NotFoundException` otherwise).
4. Build one `TransactWriteItemsEnhancedRequest`:
   - per note: `deleteNoteInTx` for the original key, and `saveNoteInTx` with
     the loaded entity mutated — `pk = DeckKeys.deckPk(targetDeckId)`,
     `deckId = targetDeckId`; `id`, GSI keys, `front`, `back`,
     `lastFormattedAt` untouched.
5. `enhancedClient.transactWriteItems(...)` — all-or-nothing.
6. After commit, `chatRepository.deleteChat(DeckKeys.deckPk(sourceDeckId),
   noteId)` for each moved note.

Chat deletion happens after the transaction commits: a failure there leaves
orphaned chat under the source partition, which is harmless and matches how
`deleteNote` already works. It is not part of the atomicity guarantee.

## Error handling

- `NotFoundException` (404): source or target deck missing; a requested note
  id not present in the source deck.
- `SmortException` (400): `sourceDeckId == targetDeckId`.
- Empty `noteIds` is a no-op success (204).
- Duplicate note ids are deduped before building the transaction.

## Other wiring

- `DeckController`: add the `moveNotes` endpoint delegating to
  `DeckService.moveNotes`.
- No terraform changes: the existing `common-table` already covers `NoteEntity`
  and chat; no new attributes or indexes.

## Testing

No tests are written unless explicitly requested (per AGENTS.md). Compilation
is skipped during implementation per AGENTS.md and verified later by the human.
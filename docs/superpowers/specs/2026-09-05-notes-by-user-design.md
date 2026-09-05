# Design: Fetch all notes for a user via `UserNoteIndex` GSI

Date: 2026-09-05

## Goal

`DeckService.getNotes(UUID deckId)` only fetches the notes of a single deck.
There is no way to fetch *all* notes belonging to a user. Add a `UserNoteIndex`
global secondary index on `NoteEntity` so a user's notes can be queried in one
go, mirroring how `UserDeckIndex` and `UserAnalysisIndex` query the current
user's decks and analyses.

## Approach

Mirror the deck-by-user and analysis-by-user patterns. Add a `UserNoteIndex`
GSI on `NoteEntity` (partition key `USER#<userId>`, sort key `NOTE#<noteId>`),
add a `userId` attribute to the note, thread the user id through
`NoteEntityMapper`, query the index by user in the repository, and expose a
service method that returns all notes for the current user.

Because per-deck note queries already exist via the primary key
(`DECK#<deckId>` / `NOTE#<noteId>`), the GSI sort key stays flat at
`NOTE#<noteId>`; no deck grouping is needed in the index.

The codebase has no auth yet, so the current user is the hardcoded dummy user
`"default"`, exactly as decks and analyses already use.

## Changes

1. **`NoteEntity`** — add `userId` field and two GSI-key fields annotated for a
   new `UserNoteIndex`:
   - `userNoteIndexGsiPk` = `@DynamoDbSecondaryPartitionKey(indexNames = "UserNoteIndex")`
   - `userNoteIndexGsiSk` = `@DynamoDbSecondarySortKey(indexNames = "UserNoteIndex")`

2. **Keys** — `NoteKeys` gains `userNoteIndexGsiPk(String userId)` returning
   `"USER#" + userId`. The GSI sort key reuses `NoteKeys.noteSk(UUID noteId)`
   (already returns `"NOTE#" + noteId`).

3. **`NoteEntityMapper`** — add `String userId` parameter to
   `toNoteEntity(UUID deckId, UUID noteId, NoteSchema noteSchema, String userId)`
   and map the two GSI-key fields and `userId`. Update call sites:
   - `DeckService.handleDerivedNotes(...)` — pass `"default"`
   - `DeckService.handleUnmappedNotes(...)` → `toNoteEntity(...)` — pass `"default"`
   - `DeckService.storeDraftNote(...)` — pass `"default"`
   - `NoteService.chat(...)` — pass `"default"`

4. **`DynamoDbClientConfig`** — add
   `@Bean DynamoDbIndex<NoteEntity> userNoteIndex(noteTable)` returning
   `noteTable.index("UserNoteIndex")`.

5. **`DeckRepository`** — add `findNotesByUserId(String userId)` querying the
   `userNoteIndex` GSI on partition key `USER#<userId>`.

6. **`DeckService`** — add `getAllNotes()` returning
   `deckRepository.findNotesByUserId("default")`, mirroring `getDecks()`.

## Data flow

`getAllNotes()` → `findNotesByUserId("default")` → GSI query → returns every
note owned by the current user.

## Error handling

Unchanged — no new failure modes beyond the existing query path.

## Testing

No new tests (per AGENTS.md, tests are only written when explicitly requested).
Compilation is owned by the human and skipped in implementation.

## Operational note

The `UserNoteIndex` GSI must be provisioned on `common-table` in DynamoDB, the
same way `UserDeckIndex` and `UserAnalysisIndex` already exist. This is an
external/infra concern, not part of the code change.
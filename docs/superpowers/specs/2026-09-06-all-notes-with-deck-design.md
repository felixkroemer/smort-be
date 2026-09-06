# Design: All-notes endpoint with stored `deckId`

Date: 2026-09-06

## Goal

Expose every note of the current user (across all decks) through an HTTP
endpoint so a front-end table can list them and join with `GET /decks` to map
each note to its deck. Note data is stored via the `UserNoteIndex` GSI
mechanism already on `NoteEntity`. Each returned note must carry its deck id
so the client can join without extra calls per row.

## Approach

Add `deckId` as an explicit, stored attribute on `NoteEntity`, populated by
the existing `NoteEntityMapper.toNoteEntity(...)` (which already receives
`deckId` as a parameter). Add a `GET /notes` endpoint backed by the existing
`DeckService.getAllNotes()` and extend `NoteResponse` with `deckId`. The
deck id is persisted with the note rather than derived from the composite
`pk` (`DECK#<deckId>`), so the attribute survives independently of the
`pk`/`sk` key structure.

`DeckService.getAllNotes()` already queries the user's notes via the
`UserNoteIndex` GSI (`findNotesByUserId("default")`), returning every
`NoteEntity` across decks. The new endpoint only needs a controller and
response/mapping changes.

The codebase has no auth yet, so the current user is the hardcoded dummy user
`"default"`, exactly as decks and notes already use.

## Changes

1. **`NoteEntity`** — add `private UUID deckId;` (a plain stored attribute;
   no key/GSI annotation).

2. **`NoteEntityMapper`** — add `@Mapping(target = "deckId", source = "deckId")`
   to `toNoteEntity(UUID deckId, UUID noteId, NoteSchema noteSchema, String userId)`
   so every note write stamps the attribute.

3. **`NoteResponse`** — become
   `record NoteResponse(UUID id, UUID deckId, String front, String back, Instant lastFormattedAt)`.

4. **`NoteRestMapper`** — no code change needed: MapStruct maps the entity's
   `deckId` field to the response field by name.

5. **`NoteController`** (new, in `application/deck/`) — `@RequestMapping("notes")`
   with `@GetMapping` returning `List<NoteResponse>` from
   `noteRestMapper.toNoteResponse(deckService.getAllNotes())`.

## Data flow

`GET /notes` → `DeckService.getAllNotes()` → `DeckRepository.findNotesByUserId("default")`
→ `UserNoteIndex` GSI query on partition key `USER#default` → every
`NoteEntity` (each carrying its stored `deckId`) → mapped to `List<NoteResponse>`.

## Error handling

None new. A user with no notes receives an empty list (`200 []`).

## Existing data

Notes written before this change lack the `deckId` attribute and will map to
`deckId: null`. This is accepted; the human will wipe existing data, so no
backfill is included.

## Testing

No new tests (per AGENTS.md, tests are only written when explicitly requested).
Compilation is owned by the human and skipped in implementation.
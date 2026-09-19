# Deck Jottings (Backlog) — Design

Date: 2026-09-19

## Overview

Each deck has a backlog: a list of jottings that capture possible future notes.
A jotting consists of a title and a description. Analyses do not have jottings;
the backlog belongs to decks only.

Internally the entity is named **Jotting** and the REST endpoints use
`/decks/{deckId}/jottings`.

## Data model

Store one DynamoDB item per jotting under the deck's partition, parallel to
notes:

- `JottingEntity` (`infrastructure/dynamodb/deck`)
  - `pk` = `DECK#<deckId>` (via `DeckKeys.deckPk`)
  - `sk` = `JOTTING#<jottingId>` (via new `JottingKeys.jottingSk`)
  - `id` (UUID), `deckId` (UUID), `title` (String), `description` (String)
  - No GSI fields and no `userId`: all reads are per-deck, so no index is needed.

- `JottingKeys` (`infrastructure/dynamodb/keys/sort`)
  - `jottingSk(UUID jottingId)` → `"JOTTING#" + jottingId`
  - `jottingPrefix()` → `"JOTTING#"`

No terraform changes: the existing `common-table` already covers the entity,
and no new attributes or indexes are required.

## Repository

`JottingRepository` (`infrastructure/dynamodb/deck`):

- `save(JottingEntity)` — `putItem`
- `findJottingsByDeckId(UUID deckId)` — query `sortBeginsWith(JOTTING#)` on
  `DECK#<deckId>` (parallel to `DeckRepository.findNotesByDeckId`)
- `findJotting(UUID deckId, UUID jottingId)` — `getItem`, returns `Optional`
- `delete(UUID deckId, UUID jottingId)` — `deleteItem`
- `deleteAllJottings(UUID deckId)` — query keys and batch-delete in chunks
  (parallel to `DeckRepository.deleteDeckNotes`)

## Service and mappers

`JottingService` (`domain/deck`):

- `JottingEntity create(UUID deckId, String title, String description)` —
  generate a new UUID for the jotting, persist, return the entity.
- `List<JottingEntity> getJottings(UUID deckId)`
- `JottingEntity update(UUID deckId, UUID jottingId, String title, String
  description)` — load the existing jotting or throw `NotFoundException`;
  apply non-null fields, persist, return it.
- `void delete(UUID deckId, UUID jottingId)` — delete the jotting.

Method results are returned as `JottingEntity`, parallel to `DeckService` /
`NoteService`.

`JottingRestMapper` (`application/deck/mapping`, MapStruct) maps
`JottingEntity` → `JottingResponse` with a `List` overload, mirroring
`NoteRestMapper`.

## DTOs

`application/deck/dto`:

- `JottingResponse(UUID id, UUID deckId, String title, String description)`
- `CreateJottingRequest(String title, String description)`
- `UpdateJottingRequest(String title, String description)` — nullable fields;
  only non-null values are applied (like `UpdateDeckSettingsRequest`)

## Endpoints

Added to `DeckController`:

```
GET    /decks/{deckId}/jottings              → List<JottingResponse>
POST   /decks/{deckId}/jottings              → 201 + JottingResponse
PATCH  /decks/{deckId}/jottings/{jottingId}  → JottingResponse
DELETE /decks/{deckId}/jottings/{jottingId}  → 204 No Content
```

## Other wiring

- `DynamoDbClientConfig`: register `jottingTable` bean on `common-table`.
- `CleanupCron.deleteDecksMarkedForDeletion`: call
  `jottingRepository.deleteAllJottings(deckId)` so jottings are removed when a
  deck is deleted.

## Error handling

- Getting the jotting in `update` throws `NotFoundException` with
  `deckId`/`jottingId` when the jotting does not exist.
- `delete` and `list` do not validate existence, matching existing per-deck
  endpoints.

## Testing

No tests are written unless explicitly requested (per AGENTS.md). Compilation
is skipped during implementation per AGENTS.md and verified later by the human.
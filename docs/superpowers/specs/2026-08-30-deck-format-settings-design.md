# Deck Format Settings — Design

Date: 2026-08-30
Status: Approved
Scope: Storage + GET/PATCH API + wiring into deck formatting flows.

## Problem

Analyses already carry per-entity settings (`formatInstructions`, exposed via
`GET/PATCH /analysis/{analysisId}/settings` and used in both the single-note
format flow and the bulk format flow). Decks have the same two formatting flows,
but they currently pass `Optional.empty()` as format instructions and expose no
settings resource. This spec adds the same settings capability for decks,
kept symmetrical to analyses.

## Decisions

- The setting lives on the `DeckMetaEntity` as a plain `Optional<String>` field
  named `formatInstructions`, persisted with the existing
  `OptionalStringConverter` (same as `AnalysisMetaEntity`).
- The `Deck` domain object gains a matching `formatInstructions` field so it
  stays aligned with the `Analysis` domain object; MapStruct maps it from the
  meta automatically.
- A `DeckSettings` domain record wraps the field, mirroring `AnalysisSettings`.
- The HTTP body is a JSON object with a named key (`"formatInstructions"`), not
  a raw string, so the contract already supports partial upsert once more
  settings exist.
- PATCH semantics: absent key = leave unchanged; `null` = clear; non-null string
  = overwrite. Same semantics as the analysis settings endpoint.
- No validation on the string contents or length.
- The setting is wired into the deck formatting flows: single-note format
  (`NoteService.formatNote`) and bulk format (`DeckBulkFormatService`).
- No changes to `DeckResponse` or `AnalysisResponse`; settings stay behind the
  dedicated `/settings` endpoint, as with analyses.
- No timestamps are written on update: unlike `AnalysisMetaEntity`,
  `DeckMetaEntity` does not track `updatedAt`, so the update simply sets the
  field and saves. This is the only deviation from strict symmetry.

## API

### GET `/decks/{deckId}/settings`

Returns the current settings.

- `200` — `{"formatInstructions": "<text>"}`, or `"formatInstructions": null`
  when unset.
- `404` — deck does not exist.

### PATCH `/decks/{deckId}/settings`

Request body is the settings object; only provided keys are applied.

- `{"formatInstructions": "<text>"}` — overwrite the text.
- `{"formatInstructions": null}` — clear the text (reset to `Optional.empty()`).
- `{}` — no-op, returns current settings.
- Response: `200` with the updated settings object (same shape as GET).

## Components

### Domain

- `DeckMetaEntity` (`infrastructure/dynamodb/deck`): add
  `@Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  Optional<String> formatInstructions = Optional.empty();`
- `Deck` (`domain/deck`): add `Optional<String> formatInstructions = Optional.empty();`
- New `DeckSettings` record (`domain/deck`): `Optional<String> formatInstructions`.

### Service

- `DeckService`:
  - `getDeckSettings(UUID deckId)` → `new DeckSettings(getMeta(deckId).getFormatInstructions())`.
  - `updateDeckSettings(UUID deckId, Optional<String> formatInstructions)`:
    load meta, and if `formatInstructions != null`, set it and save via
    `deckRepository.saveDeckMeta`, then return
    `new DeckSettings(meta.getFormatInstructions())`.

### API layer

- New DTOs (`application/deck/dto`):
  - `DeckSettingsResponse(Optional<String> formatInstructions)`
  - `UpdateDeckSettingsRequest(Optional<String> formatInstructions)`
- `DeckRestMapper`: add `DeckSettingsResponse toDeckSettingsResponse(DeckSettings settings);`
- `DeckController`:
  - `GET /{deckId}/settings` → `deckRestMapper.toDeckSettingsResponse(deckService.getDeckSettings(deckId))`
  - `PATCH /{deckId}/settings` → `deckRestMapper.toDeckSettingsResponse(deckService.updateDeckSettings(deckId, updateDeckSettingsRequest.formatInstructions()))`

### Formatting wiring

- `NoteService`: inject `DeckService`; in `formatNote`, replace `Optional.empty()`
  with `deckService.getDeckSettings(deckId).formatInstructions()`.
- `DeckBulkFormatService`: inject `DeckService`; in `processNotes`, replace
  `Optional.empty()` with `deckService.getDeckSettings(deckId).formatInstructions()`.

No new tests (per AGENTS.md). Compilation is verified by the human later.
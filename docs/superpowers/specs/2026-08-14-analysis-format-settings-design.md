# Analysis Format Settings — Design

Date: 2026-08-14
Status: Approved
Scope: Storage + GET/PATCH API only. The setting is NOT wired into OpenAI formatting yet.

## Problem

Each analysis should be able to carry its own formatting configuration. The first
setting is a free-text block (`formatInstructions`) describing how notes should be
formatted. A per-analysis settings resource is exposed over HTTP with GET and PATCH
semantics designed to stay stable as more settings are added later.

## Decisions

- The setting lives on the Analysis entity as a plain `Optional<String>` field.
- The HTTP body is a JSON object with a named key (`"formatInstructions"`), not a
  raw string, so the API contract already supports partial upsert once more
  settings exist.
- PATCH semantics: absent key = leave unchanged; `null` = clear; non-null string =
  overwrite.
- No validation on the string contents or length.
- Not wired into OpenAI formatting in this task.

## API

### GET `/analysis/{analysisId}/settings`

Returns the current settings.

- `200` — `{"formatInstructions": "<text>"}`, or `"formatInstructions": null`
  (serialized like the existing `AnalysisResponse.deckId` `Optional`) when unset.
- `404` — analysis does not exist.

### PATCH `/analysis/{analysisId}/settings`

Request body is the settings object; only provided keys are applied.

- `{"formatInstructions": "<text>"}` — overwrite the text.
- `{"formatInstructions": null}` — clear the text (reset to `Optional.empty()`).
- `{}` — no-op, returns current settings.
- Response: `200` with the updated settings object (same shape as GET).
- `404` — analysis does not exist.

## Architecture

Follows the existing layered pattern (application → domain → infrastructure).

### infrastructure/dynamodb/anki/AnalysisMetaEntity.java

- Add field, using the existing `OptionalStringConverter` pattern from
  `AbstractChatMessageEntity`:
  `@DynamoDbConvertedBy(OptionalStringConverter.class) Optional<String> formatInstructions`
- Setter assignment is handled by Lombok `@Setter`; the constructor initializes the
  field to `Optional.empty()` implicitly (null on the entity). No new table, index,
  key helper, or repository.

### domain/anki/Analysis.java

- Add `Optional<String> formatInstructions`.

### domain/anki/AnalysisService.java

- `public Optional<String> getFormatSettings(UUID analysisId)` — loads meta via the
  existing private `getMeta` (throws `NotFoundException`), returns the Optional.
- `public void updateFormatSettings(UUID analysisId, Optional<String> formatInstructions)` —
  loads meta, sets the field, bumps `updatedAt`, saves via `analysisMetaRepository.save`.
  `Optional.empty()` clears; the existing `OptionalStringConverter` persists it as
  absent. Only called when the request actually carries the key (see controller).

### application/anki/AnalysisController.java

- `@GetMapping("/{analysisId}/settings")` → `FormatSettingsResponse`.
- `@PatchMapping("/{analysisId}/settings")` with `@RequestBody UpdateFormatSettingsRequest`
  → `FormatSettingsResponse` (returns updated settings).
- PATCH absent-vs-null handling: the request component is `null` when the key is
  absent (leave unchanged — do not call the service), `Optional.empty()` when the
  JSON value is `null` (clear), `Optional.of(...)` when a string (overwrite).

### application/anki/dto/

- `FormatSettingsResponse(Optional<String> formatInstructions)`.
- `UpdateFormatSettingsRequest(Optional<String> formatInstructions)` — Jackson 3
  (Boot 4, `tools.jackson`) handles `Optional` natively, same as the existing
  `AnalysisResponse.deckId` precedent: missing key → component `null`, explicit
  `null` → `Optional.empty()`, string → `Optional.of`.

### application/anki/mapping/AnalysisRestMapper.java

- Add `FormatSettingsResponse toFormatSettingsResponse(Optional<String> formatInstructions)`
  to `AnalysisRestMapper`, mapping the `Optional` through unchanged.

## Error handling

- Non-existent analysis → existing `NotFoundException` (from `AnalysisService.getMeta`),
  handled by `GlobalExceptionHandler` → `404`.

## Out of scope

- Wiring `formatInstructions` into OpenAI formatting (`AnkiNoteService.formatNote`,
  `BulkFormatService`).
- Additional settings beyond `formatInstructions`.

## Testing

- Manual verification against `local` DynamoDB: GET before set (null), PATCH set,
  GET returns value, PATCH null clears, PATCH on missing analysis → 404.
- No unit tests follow the current repo state (tests were removed in earlier commits).

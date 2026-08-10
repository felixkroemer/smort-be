# Add `lastFormattedAt` to Derived Notes

**Date:** 2026-08-10

**Status:** Approved design

## Goal

Track when a derived note was last formatted. Add an internal `lastFormattedAt` timestamp to `DerivedNoteEntity` (the derived notes that belong to an Analysis). It is set on explicit format operations only — the single-note format endpoint and the bulk-format job. Notes created via chat are not stamped, and the field is not exposed through the REST API for now.

## Decisions (from brainstorming)

| Question | Decision |
|---|---|
| When to set the field | **Format operations only** — `AnkiNoteService.formatNote` (single-note format) and `BulkFormatService.processNotes` (bulk format) stamp it; chat-created derived notes stay `Optional.empty()` |
| Expose via API? | **No** — internal only for now; `DerivedNoteResponse`, `AnkiNoteMapper`, and the REST API surface are unchanged |
| Storage type | **`Optional<Instant>`** with a dedicated attribute converter, mirroring the existing `Optional<String>` pattern (`AbstractChatMessageEntity` + `OptionalStringConverter`) |
| Existing data | **No migration** — records without the attribute read back as `Optional.empty()` |

## Component design

### 1. `OptionalInstantConverter` — new, `infrastructure/dynamodb/`

Attribute converter mirroring `OptionalStringConverter`, specialized for `Instant`:

- `transformFrom(Optional<Instant>)` — present value stored as ISO-8601 string (`AttributeValue.S`), empty → `AttributeValue` with `nul(true)`
- `transformTo(AttributeValue)` — `nul(true)` → `Optional.empty()`, otherwise parse `s()` into `Instant`
- `type()` → `EnhancedType.optionalOf(Instant.class)`
- `attributeValueType()` → `AttributeValueType.S`

### 2. `DerivedNoteEntity` — modified, `infrastructure/dynamodb/anki/`

- Add `private Optional<Instant> lastFormattedAt = Optional.empty();`
- Getter annotated `@Getter(onMethod_ = @DynamoDbConvertedBy(OptionalInstantConverter.class))`, same pattern as `AbstractChatMessageEntity` fields
- Default `Optional.empty()` so chat-created notes and pre-existing records deserialize as empty without migration

### 3. `AnkiNoteService.formatNote` — modified, `domain/anki/`

Stamps `Optional.of(Instant.now())` on both paths:

- existing derived note being updated (`.map(...)`)
- new derived note created via `.orElseGet(...)`

`chat(...)` is untouched — derived notes created from chat keep `lastFormattedAt` empty.

### 4. `BulkFormatService.processNotes` — modified, `domain/anki/`

Stamps `Optional.of(Instant.now())` on each `DerivedNoteEntity` created during bulk formatting.

## Error handling

- No new error paths. The converter's `transformTo` handles both stored string values and NULL attributes.
- `Optional.empty()` (chat-created / legacy notes) is a valid state, not an error.

## Testing / verification

- `./mvnw compile` — compile clean
- `./mvnw test` — context-load smoke test (`SmortApplicationTests`) is pre-existing-broken in this environment and not part of this work's gate (consistent with the previous spec)
- No unit tests exist for the affected classes; behavior is verified by compile, consistent with the current repo state

## Out of scope

- Exposing `lastFormattedAt` in `DerivedNoteResponse` / REST API (future work)
- Stamp on chat-created notes
- Data migration for existing derived notes

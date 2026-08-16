# Deck Bulk Format — Design

Date: 2026-08-16
Branch: `feat/deck-bulk-format`

## Overview

Bulk format currently exists only for analyses: it LLM-formats `AnkiNote`s (sqlite) into
`DerivedNoteEntity`s (DynamoDB), tracked by a `BulkFormatEntity` job. This work extends the
same capability to imported decks, where the target is the `NoteEntity` itself, mutated in
place.

Approach chosen: **Option B** — two domain services (`BulkFormatService` for analyses,
`DeckBulkFormatService` for decks) sharing one generic `BulkFormatEngine` that holds the
loop/retry/status machinery common to both.

## Goals

- Deck bulk format lifecycle matching the analysis one: start, status, cancel endpoints.
- Crash-resume of deck jobs via the existing cron, routed to the deck service.
- Zero duplication of the loop/retry/status machinery; analysis behavior preserved exactly.
- `reformatAlreadyFormatted` semantics for decks, mirroring analysis via a new
  `NoteEntity.lastFormattedAt`.

## Non-goals

- Deck-specific format instructions (decks always use default formatting, as
  `NoteService.formatNote` does today).
- Changing single-note format behavior.
- Any change to existing analysis bulk-format behavior.

## Design decisions

1. **Two services + shared engine.** The domains differ on every axis that matters (note
   source types, persistence target, job keying, format instructions), so a single generic
   service would leak both domains into one class. What is genuinely identical — the async
   loop, attempts/consecutive-failure handling, status transitions, cancellation safety — is
   extracted into `BulkFormatEngine`.
2. **Abstract `BulkFormatEntity` base** with one subclass per domain, each constructor
   setting the correct partition key and its own sort key.
3. **Distinct sort keys per domain** so the resume cron can tell job types apart.
4. **`NoteEntity.lastFormattedAt`** added, mirroring `DerivedNoteEntity.lastFormattedAt`, so
   the same skip-already-formatted filter works for decks.

## Components

### `domain/common`

- `BulkFormatEngine` (new, `@Service`). Depends only on `BulkFormatRepository` and
  `bulkFormatTaskExecutor`; never references Anki/Deck/ChatService.
  - Constants `MAX_ATTEMPTS`, `MAX_RECENT_FAILED` (moved from `BulkFormatService`).
  - `dispatch(BulkFormatEntity job, Runnable task)` — async execution plus
    `BulkFormatCancelledException`/error logging, keyed off `job.getOwnerId()`.
  - `<T> void process(BulkFormatEntity job, List<T> items, ItemProcessor<T> processor)` —
    the whole `processNotes` loop: set `IN_PROGRESS`, bump `attempts`, iterate items,
    count `processed`/`failed`, break on `consecutiveFailed >= MAX_RECENT_FAILED`, per-item
    progress save **outside** the per-item try/catch (so cancellation propagates), then the
    `COMPLETED` / `FAILED` / `WAITING_RETRY` transition.
  - `cancel(BulkFormatEntity job)` — set `CANCELLED` if active and save.
  - `assertNoActiveJob(Optional<? extends BulkFormatEntity> existing)` — the shared
    already-in-progress guard used by both `startBulkFormat` methods.
  - `@FunctionalInterface ItemProcessor<T> { void process(T item) throws Exception; }`
- `BulkFormat` (moved from `domain/anki`) — shared status/status DTO shape.
- `mapping/BulkFormatEntityMapper` (moved from `domain/anki/mapping`) — maps the abstract
  base to `BulkFormat`; works for both subclasses.

### `infrastructure/dynamodb`

- `BulkFormatStatus` (moved from `.../dynamodb/anki`) — shared enum.
- `BulkFormatEntity` (moved from `.../dynamodb/anki`, now `abstract`, `@DynamoDbBean`):
  common mapped fields (`pk`, `sk`, `status`, `createdAt`, `lastUpdatedAt`, `totalNotes`,
  `completedNotes`, `attempts`, `reformatAlreadyFormatted`, the two `StatusBulkFormatIndex`
  GSI fields) and abstract `UUID getOwnerId()`.
- `anki/AnalysisBulkFormatEntity extends BulkFormatEntity` (`@DynamoDbBean`): ctor
  `(UUID analysisId, boolean reformat)`; `pk = AnalysisKeys.analysisPk(analysisId)`,
  `sk = BulkFormatKeys.bulkFormatSk()`; `getAnalysisId()` parses `ANALYSIS#`; `getOwnerId()`.
- `deck/DeckBulkFormatEntity extends BulkFormatEntity` (`@DynamoDbBean`): ctor
  `(UUID deckId, boolean reformat)`; `pk = DeckKeys.deckPk(deckId)`,
  `sk = BulkFormatKeys.deckBulkFormatSk()`; `getDeckId()` parses `DECK#`; `getOwnerId()`.
- `keys/sort/BulkFormatKeys`: keep `bulkFormatSk()` (`META#BULKFORMAT#`), add
  `deckBulkFormatSk()` (`META#BULKFORMAT#DECK#`).
- `BulkFormatRepository`:
  - Two table beans (`DynamoDbTable<AnalysisBulkFormatEntity>`,
    `DynamoDbTable<DeckBulkFormatEntity>`, both on `common-table`) and two
    `StatusBulkFormatIndex` index beans.
  - `findBulkFormatByAnalysisId(UUID)`, `findBulkFormatByDeckId(UUID)` — `getItem` with the
    domain's `pk`/`sk`.
  - `save(AnalysisBulkFormatEntity)` and `save(DeckBulkFormatEntity)` overloads — same
    conditional-expression write (cancellation guard).
  - `findAllActive()` → `List<BulkFormatEntity>` — for each active status, query each
    type's index with a filter expression on `sk` equal to that type's sort key, then merge.
  - `delete(UUID analysisId)` (existing), `deleteDeckJob(UUID deckId)` (new).
- `DynamoDbClientConfig`: wire the two bulk-format tables and two index beans.

### `infrastructure/dynamodb/deck`

- `NoteEntity`: add `Optional<Instant> lastFormattedAt`
  (`@DynamoDbConvertedBy(OptionalInstantConverter.class)`, default `Optional.empty()`).

### `domain/anki` — `BulkFormatService` refactor

- Public API unchanged: `startBulkFormat`, `resumeBulkFormat`, `cancelBulkFormat`,
  `getJobStatus`.
- Internals switched to `AnalysisBulkFormatEntity` and to the engine for
  dispatch/process/cancel/active-guard.
- Domain processor: `chatService.formatNote(content, analysis.getFormatInstructions())`,
  update-or-create `DerivedNoteEntity` (via `DerivedNoteEntityMapper`), save via
  `derivedNoteRepository`.

### `domain/deck` — `DeckBulkFormatService` (new)

- `startBulkFormat(UUID deckId, boolean reformatAlreadyFormatted)`,
  `resumeBulkFormat(DeckBulkFormatEntity)`, `cancelBulkFormat(UUID deckId)`,
  `getJobStatus(UUID deckId)`.
- Sources notes via `deckRepository.findNotesByDeckId`.
- Candidate filter: skip a note when `!reformatAlreadyFormatted` and its
  `lastFormattedAt` is after the job's `createdAt`.
- Domain processor: `chatService.formatNote(note.getContent(), Optional.empty())`, set
  `front`/`back`/`lastFormattedAt` on the `NoteEntity`, save via `deckRepository.saveNote`.

### `application/deck`

- `DeckController` endpoints (mirroring `AnalysisController`):
  - `POST /decks/{deckId}/format` (`reformatAlreadyFormatted` param, `202 ACCEPTED`)
  - `GET /decks/{deckId}/format/status` → `BulkFormatResponse`
  - `POST /decks/{deckId}/format/cancel` (`202 ACCEPTED`)
- Reuses the existing `BulkFormatResponse` DTO and `BulkFormatRestMapper` from
  `application/anki`.

### `domain/cron` — `BulkFormatCron`

- `findAllActive()` returns both job types. For each job past `CRASH_TIMEOUT`, route by
  `sk`: `bulkFormatSk()` → `BulkFormatService.resumeBulkFormat((AnalysisBulkFormatEntity))`;
  `deckBulkFormatSk()` → `DeckBulkFormatService.resumeBulkFormat((DeckBulkFormatEntity))`.

## Data flow (deck)

1. `startBulkFormat`: guard against an active job → load `NoteEntity`s → filter candidates
   → throw `BAD_REQUEST` if empty → create `DeckBulkFormatEntity`, set `totalNotes`, save →
   `engine.dispatch(job, () -> processNotes(job))`.
2. `processNotes`: reload notes, recompute candidates (so resume picks up fresh
   `lastFormattedAt`) → `engine.process(job, candidates, deckProcessor)`.
3. Loop: per item, format + mutate + save; counts and progress as in the engine; ends in
   `COMPLETED`/`FAILED`/`WAITING_RETRY`.
4. Cron: crash timeout → route by `sk` → resume.

## Error handling

- Per-item failures increment `failed`; `MAX_RECENT_FAILED` consecutive failures break the
  loop (unchanged).
- Cancellation surfaces via the conditional progress write throwing
  `BulkFormatCancelledException`, which aborts the loop and is logged in `dispatch`.
- No candidates → `SmortException(HttpStatus.BAD_REQUEST, ...)` in each service.

## Engine vs services split

| Generic (`BulkFormatEngine`)                                   | Per-domain (services)                                    |
| -------------------------------------------------------------- | -------------------------------------------------------- |
| async `dispatch` + cancellation/error logging                  | job creation & keying (`AnalysisBulkFormatEntity`/`DeckBulkFormatEntity`) |
| `process` loop, counters, consecutive-failure break, progress  | note sourcing (`getNotes`/`findNotesByDeckId`)           |
| status transitions `COMPLETED`/`FAILED`/`WAITING_RETRY`        | candidate selection (`reformatAlreadyFormatted` filter)  |
| `cancel`, active-job guard                                     | per-item processor (formatting call + persistence)       |
| constants `MAX_ATTEMPTS`, `MAX_RECENT_FAILED`                  | job lookup/status mapping                                |

## Assumptions / verification points

- The DynamoDB Enhanced Client supports `@DynamoDbBean` on an abstract base with annotated
  inherited getters and `@DynamoDbBean` on concrete subclasses (verify at implementation).
- The `StatusBulkFormatIndex` GSI projects all attributes, so an `sk` filter expression on
  the index query works (verify; `findAllActive` already deserializes full beans today).
- `BulkFormatResponse`/`BulkFormatRestMapper` are reused from `application/anki` by the deck
  controller (a cross-domain REST import; move to `application/common` only if preferred).

## Out of scope

- Deck format-instruction settings.
- `NoteService.formatNote` (single-note) persistence behavior.
- Job retention/failure cleanup policy.
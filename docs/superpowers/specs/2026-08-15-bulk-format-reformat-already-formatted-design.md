# Bulk Format: Reformat Already Formatted Notes

Date: 2026-08-15

## Problem

The bulk format flow only formats notes that do not yet have a derived note. There is no way to
re-run formatting on notes that were already formatted (e.g. because the format instructions
changed). This spec adds an optional `reformatAlreadyFormatted` flag that lets the user reformat
already-formatted notes whose derived note is older than the bulk format job.

## Behavior

`processNotes` selects a note for formatting when:

- the note has **no** derived note, **or**
- `reformatAlreadyFormatted == true` **and** the derived note's `lastFormattedAt` is before the
  bulk format job's `createdAt` (a missing `lastFormattedAt` is treated as "formatted before
  createdAt", i.e. it is reformatted).

## Changes

### `BulkFormatEntity`

- Keep `totalNotes` (set once at job start, see service below).
- Add `boolean reformatAlreadyFormatted`.
- Constructor becomes `BulkFormatEntity(UUID analysisId, boolean reformatAlreadyFormatted)`.

No migration is needed; the DynamoDB data will be cleared.

### `BulkFormatService`

`startBulkFormat(UUID analysisId, boolean reformatAlreadyFormatted)`:

1. Run the existing no-active-job validation.
2. Fetch `analysis`, the deck's notes, and the existing derived notes (keyed by noteId).
3. Create the job (not yet saved), then compute `notesToProcess` using the filter above with
   `job.getCreatedAt()`.
4. If `notesToProcess` is empty, throw a `SmortException` with `LogSeverity.INFO` and a message
   indicating there are no notes to format for the analysis. No job is persisted in this case.
5. `job.setTotalNotes(notesToProcess.size())`, save the job.
6. `dispatch(job, notesToProcess)`.

`notesToProcess` is a `List<NoteToProcess>` where `NoteToProcess` pairs each eligible `AnkiNoteEntity`
with its existing `DerivedNoteEntity` (null when the note is not yet formatted). The filter itself is
unchanged (see Behavior); the derived note travels with the anki note so the processing loop knows
what content to format.

New overloads so the first attempt does not recompute the filter:

- `dispatch(BulkFormatEntity job, List<NoteToProcess> notesToProcess)`
  → `processNotes(job, notesToProcess)`.
- `processNotes(BulkFormatEntity job, List<NoteToProcess> notesToProcess)`:
  the existing processing loop, but **removes** `setCompletedNotes(existingDerivedNotes.size())` and
  `setTotalNotes(notes.size())`; keeps the `setCompletedNotes(getCompletedNotes() + 1)` increment
  per successfully formatted note. Still fetches `analysis` and `noteTypes` inside. The loop mirrors
  `AnkiNoteService.formatNote`:
  - When the note already has a derived note, the content sent to `chatService.formatNote` is that
    derived note's `front`/`back` (not the anki fields), and the result **updates** the existing
    derived note (`setFront`/`setBack`/`setLastFormattedAt`).
  - When the note is not yet formatted, the content is the anki note's fields (as before) and a new
    `DerivedNoteEntity` is created.
- `resumeBulkFormat(BulkFormatEntity job)` → `dispatch(job)` → `processNotes(job)`, which computes
  analysis/notes/derived and the filter itself, then delegates to the overloaded `processNotes`.
  Uses the persisted `totalNotes`; does not touch it.
- `getNotesToProcess(List<AnkiNoteEntity>, Map<Long, DerivedNoteEntity>, BulkFormatEntity)` is the
  shared filter helper; it maps each surviving note to a `NoteToProcess`.

### `AnalysisController`

`POST /analysis/{analysisId}/format` accepts a query parameter:
`@RequestParam(defaultValue = "true") boolean reformatAlreadyFormatted`. Default is `true`.

### `BulkFormat` and `BulkFormatResponse`

Keep `totalNotes`. `reformatAlreadyFormatted` is not exposed in the status response.

## Out of Scope

- Frontend changes (separate repository).
- DynamoDB data migration (database will be cleared).
- New tests (none exist for this service; compilation is skipped per AGENTS.md).

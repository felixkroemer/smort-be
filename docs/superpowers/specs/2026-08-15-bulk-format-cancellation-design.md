# Bulk Format Cancellation — Design

Date: 2026-08-15
Status: Approved

## Problem

`BulkFormatService` runs bulk-format jobs asynchronously via `TaskExecutor.execute(Runnable)`,
which gives no handle to stop a running or queued job. Users need a way to cancel a bulk format.

Requirements:
- Cancellation must be graceful: stop between notes, keeping notes already formatted.
- Must work for queued (PENDING), running (IN_PROGRESS), and waiting-for-retry (WAITING_RETRY)
  jobs.
- Must remain correct if the service later runs multiple instances (DB state is the source of truth).

## Approach

DB-status-only cancellation, no in-memory Future/flag state:

- A running task discovers cancellation by polling the job status in DynamoDB between notes.
- A custom atomic DynamoDB update guards the PENDING/WAITING_RETRY → IN_PROGRESS transition so a
  task that starts or resumes after a cancel cannot overwrite `CANCELLED`.
- Everything else uses the existing plain `findBulkFormatByAnalysisId` + `save`.

Only one custom DDB method is introduced: `transitionToInProgress`.

## Design

### 1. `BulkFormatStatus`

Add `CANCELLED` to the enum.

### 2. `BulkFormatRepository`

Add one method using the low-level `DynamoDbClient.updateItem` (reached via
`enhancedClient.dynamoDbClient()`; table name from `bulkFormatTable.tableName()`):

```java
boolean transitionToInProgress(UUID analysisId, int attempts)
```

- Condition expression: `status <> CANCELLED`.
- Update expression sets: `status = IN_PROGRESS`, `attempts = :attempts`,
  `lastUpdatedAt = now`, `statusBulkFormatIndexGsiPk = IN_PROGRESS`,
  `statusBulkFormatIndexGsiSk = now`.
- Returns `false` when `ConditionalCheckFailedException` is thrown (job was cancelled or already
  terminal), `true` otherwise.

No other repository changes. `findBulkFormatByAnalysisId`, `save`, `findAllActive`, `delete`
remain as-is.

### 3. `BulkFormatService`

#### `processNotes(BulkFormatEntity job, List<NoteToProcess> notesToProcess)`

Replace the unconditional status block (currently `job.setStatus(IN_PROGRESS); save(job)`) with:

```java
int attempts = job.getAttempts() + 1;
if (!bulkFormatRepository.transitionToInProgress(analysisId, attempts)) {
  log.info("Bulk format skipped, already cancelled. analysisId={}", analysisId);
  return;
}
job.setStatus(BulkFormatStatus.IN_PROGRESS);
job.setAttempts(attempts);
```

Loop changes (only on the success path of each note, mirroring current behavior):

1. After processing a note and incrementing `job.completedNotes`, do a fresh read
   (`findBulkFormatByAnalysisId`) of the job status.
2. If the status is `CANCELLED`: set a local `cancelled = true` and `break` before saving.
3. Otherwise: `job.setLastUpdatedAt(Instant.now()); bulkFormatRepository.save(job)`.

After the loop:

```java
if (cancelled || isCancelled(analysisId)) {
  log.info("Bulk format cancelled. analysisId={}", analysisId);
  return;
}
handleProcessNotesResult(job, processed, failed, analysisId);
```

`isCancelled` is a fresh status read. The check is needed even when the loop exited normally: a
cancel can land while notes are failing, before the consecutive-failure break fires, and the
`cancelled` flag would never be set.

Skipping `handleProcessNotesResult` on cancellation is required: it would otherwise write
`COMPLETED`/`FAILED`/`WAITING_RETRY` and clobber the `CANCELLED` already stored in DDB.

#### `cancelBulkFormat(UUID analysisId)`

```java
public void cancelBulkFormat(UUID analysisId) {
  var job = bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)
      .orElseThrow(() -> new NotFoundException("No bulk format job found. analysisId={}", analysisId));
  if (job.getStatus() == PENDING || job.getStatus() == IN_PROGRESS || job.getStatus() == WAITING_RETRY) {
    job.setStatus(BulkFormatStatus.CANCELLED);
    bulkFormatRepository.save(job);
  }
}
```

Plain read-modify-write whole-item save. Idempotent; a terminal job (COMPLETED/FAILED/CANCELLED)
is left untouched.

### 4. `AnalysisController`

```java
@PostMapping("/{analysisId}/format/cancel")
@ResponseStatus(HttpStatus.ACCEPTED)
public void cancelBulkFormat(@PathVariable UUID analysisId) {
  bulkFormatService.cancelBulkFormat(analysisId);
}
```

### 5. No changes needed

- `BulkFormatCron`: `findAllActive()` only queries the GSI for `IN_PROGRESS` and
  `WAITING_RETRY`, so `CANCELLED` jobs are never auto-resumed. The resume dispatch is also
  guarded by `transitionToInProgress`.
- `DeckService` active-job guard: `CANCELLED` is not in its active set.
- `BulkFormat` domain, `BulkFormatEntityMapper`, `BulkFormatRestMapper`, `BulkFormatResponse`:
  status serializes by name, so the frontend sees `"CANCELLED"` with no mapping changes.
- `startBulkFormat`: `CANCELLED` is not in the "already in progress" guard, so a fresh run after
  cancel is allowed and overwrites the old record.

## Race analysis

| Scenario | Outcome |
| --- | --- |
| Cancel while queued (PENDING) | Task dequeues → `transitionToInProgress` condition fails → aborts, never processes |
| Cancel while running (IN_PROGRESS) | Per-note status read sees `CANCELLED` → breaks before the progress save; DB keeps `CANCELLED` |
| Cancel while WAITING_RETRY or racing the cron resume | Resume dispatch → `transitionToInProgress` condition fails → aborts |
| Cancel after COMPLETED/FAILED | `cancelBulkFormat` status guard → no-op |

Known residual race (accepted): on the success path, the status read sits between the note's
LLM call and the progress save. If a cancel write lands in the microseconds after the read but
before the save, that note's save clobbers `CANCELLED` back to `IN_PROGRESS` and the loop
continues. In the common case — cancel landing during the seconds-long LLM call — the read sees
`CANCELLED` and the loop stops. Closing this window fully would require a conditional update on
the progress save, which is out of scope.

## Error handling

- `cancelBulkFormat` on a missing job → `NotFoundException` (404).
- `transitionToInProgress` failing for any reason other than the condition (transient DDB errors)
  propagates; the current `dispatch` catch-all logs it. Behaviour unchanged from today.

## Testing

Unit tests for `BulkFormatService` with Mockito (available via `spring-boot-starter-webmvc-test`):

- Cancel during IN_PROGRESS: `transitionToInProgress` returns true; per-note status reads return
  `IN_PROGRESS` then `CANCELLED`; verify the loop stops, no terminal status is saved
  (`handleProcessNotesResult` not reached).
- Queued start after cancel: `transitionToInProgress` returns false; verify no `formatNote` calls
  and job stays `CANCELLED`.
- Cancel on a COMPLETED job: no status change, idempotent.
- Re-start after cancel: `startBulkFormat` does not throw for an existing `CANCELLED` job.
- Controller: `POST /{analysisId}/format/cancel` returns 202 and invokes the service.

Compilation and test execution are skipped by the implementing subagent per AGENTS.md.

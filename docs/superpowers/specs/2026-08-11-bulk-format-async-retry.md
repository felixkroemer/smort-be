# Async Bulk Format with `WAITING_RETRY` State

**Date:** 2026-08-11

**Status:** Approved design

## Goal

Make the bulk format feature responsive for the frontend and make failures visible.

Today `POST /analysis/{id}/format` runs the entire job synchronously in the HTTP request thread — the frontend awaits it, so progress is never observable while a job runs. A run that ends with failures silently stays `IN_PROGRESS` until the cron auto-resumes it, giving the client no signal that anything is wrong.

Three changes:

1. Run processing on a background executor so the start request returns 202 immediately.
2. Add a `WAITING_RETRY` status that explicitly marks a paused job awaiting the cron auto-resume.
3. Expose `failedCount` and `attempts` in the polling response.

## Decisions (from brainstorming)

| Question | Decision |
|---|---|
| Async mechanism | **Hardcoded `ThreadPoolTaskExecutor` bean** (core 1 / max 2 / queue 10) — no `@Async`, no configurable sizing |
| Concurrency guards | **None** — no in-memory lock; rely on existing DB-status guards |
| Partial-failure signal | **New `WAITING_RETRY` status** set by `processNotes` when failures occurred and attempts remain |
| Failure detail surfaced | **`failedCount` + `attempts` only** — per-note IDs/reasons out of scope |
| Retry cadence | **Unchanged** — `BulkFormatCron` (runs every 1 min) resumes active jobs idle > 2 min; `WAITING_RETRY` jobs are included |

## Component design

### 1. `BulkFormatConfig` — new, `infrastructure/config/`

`@Configuration` exposing the executor, hardcoded (not configurable):

```java
@Bean
TaskExecutor bulkFormatTaskExecutor() {
  var executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(1);
  executor.setMaxPoolSize(2);
  executor.setQueueCapacity(10);
  executor.setThreadNamePrefix("bulk-format-");
  executor.initialize();
  return executor;
}
```

### 2. `BulkFormatStatus` — modified, `infrastructure/dynamodb/anki/`

Add `WAITING_RETRY`.

### 3. `BulkFormatService` — modified, `domain/anki/`

- Inject the `TaskExecutor`.
- `startBulkFormat`: guard now treats `WAITING_RETRY` as active (reject). After validating and saving the fresh `IN_PROGRESS` job, dispatch:
  ```java
  bulkFormatTaskExecutor.execute(() -> processNotes(analysisId, job));
  ```
- `resumeBulkFormat`: load job, set `IN_PROGRESS` + `lastUpdatedAt = now`, save, then dispatch `processNotes` the same way.
- The dispatched task body wraps `processNotes` in a try/catch that logs unexpected exceptions; the job stays active and the cron's crash-recovery resumes it.
- `processNotes`:
  - reset `failedCount` to 0 at run start;
  - persist `failedCount` on each save;
  - final else-branch ("had errors, will resume later") sets `job.setStatus(BulkFormatStatus.WAITING_RETRY)` and saves, instead of leaving `IN_PROGRESS`.

### 4. `BulkFormatEntity` — modified, `infrastructure/dynamodb/anki/`

Add `private int failedCount;`.

### 5. `BulkFormatRepository` — modified, `infrastructure/dynamodb/anki/`

Rename `findAllInProgress()` → `findAllActive()`. Query the `StatusBulkFormatIndex` GSI for both the `IN_PROGRESS` and `WAITING_RETRY` partitions (two partition-key queries, results combined).

### 6. `BulkFormatCron` — modified, `domain/cron/`

Switch to `findAllActive()`. The 2-minute idle timeout and 1-minute schedule are unchanged.

### 7. `BulkFormat` (domain) + `BulkFormatMapper` — modified, `domain/anki/`

- `BulkFormat` gains `failedCount` and `attempts`.
- MapStruct maps both by name; no mapper code changes.

### 8. `BulkFormatStatusResponse` + `AnalysisController` — modified, `application/anki/`

Record becomes:

```java
public record BulkFormatStatusResponse(
    String status, Instant createdAt, Instant lastUpdatedAt, int totalNotes,
    int completedNotes, int failedCount, int attempts) {}
```

`AnalysisController.getBulkFormatStatus` passes `job.getFailedCount()` and `job.getAttempts()`.

### 9. `DeckService.importDeck` — pending guard, `domain/deck/`

The in-flight import guard (Task 6 of the bulk-note-formatting plan) must treat `WAITING_RETRY` as an active job and block import, alongside `IN_PROGRESS`/`PENDING`.

## Error handling

- Per-note failures unchanged: caught, counted, `MAX_RECENT_FAILED` early break.
- Unexpected run-level exceptions are caught and logged inside the executor task; the job stays active and the cron resumes it after the idle timeout.
- Because processing now runs on a background thread, no exception reaches a request/response — the task must never die silently.

## Data flow

1. Frontend `POST /analysis/{id}/format` → 202 immediately (job `IN_PROGRESS`).
2. Frontend polls `GET /analysis/{id}/format/status` (~1–2 s) → `completedNotes`/`totalNotes` progress, `failedCount`, `attempts`.
3. Partial failure → `status = WAITING_RETRY`, `failedCount = N`, `attempts = 1/2`.
4. Cron resumes the job after the idle timeout → `IN_PROGRESS` again, attempts incremented.
5. Terminal states: `COMPLETED` or `FAILED`.

## Testing / verification

- New `BulkFormatServiceTest` (JUnit + Mockito, available locally) covering:
  - all notes succeed → `COMPLETED`, `failedCount = 0`;
  - partial failure with attempts remaining → `WAITING_RETRY`, `failedCount = N`, `attempts = 1`;
  - retry exhausts `MAX_ATTEMPTS` → `FAILED`;
  - `resumeBulkFormat` sets `IN_PROGRESS` and reprocesses only notes without derived records.
- This environment has JDK 21 while the project targets Java 25, so `./mvnw compile`/`test` cannot run here (pre-existing limitation, consistent with the previous spec). Tests are written to run in a JDK-25 environment.

## Out of scope

- Per-note failure details (note IDs / reasons)
- SSE / WebSocket push
- Concurrency guards
- Configurable executor sizing

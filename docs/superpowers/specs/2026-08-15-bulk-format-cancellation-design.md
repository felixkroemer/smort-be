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
- `CANCELLED` is persisted in DynamoDB so the status endpoint, UI, and cron behave correctly, but
  cancellation is **not** detected by polling the DB in the loop.

## Approach

Future-based cancellation:

- Run tasks via `AsyncTaskExecutor.submit(Runnable)`, which returns a `Future<?>`, and keep a
  registry of `analysisId -> Future<?>`.
- `cancelBulkFormat` persists `CANCELLED` and then calls `future.cancel(true)`, which interrupts
  the running thread and prevents a queued task from ever starting.
- The running loop cooperates by checking `Thread.currentThread().isInterrupted()` at each note
  boundary (in-memory, no DB read) and stopping cleanly.
- One plain DB status check guards the task start so a task that dequeues/resumes after a cancel
  cannot clobber `CANCELLED` with `IN_PROGRESS`.

No conditional/atomic DynamoDB updates are used. Per-note DB polling is explicitly out of scope
for this iteration (it may be revisited later to support multi-instance cancellation, where the
interrupt cannot reach the JVM running the job).

## Design

### 1. `BulkFormatConfig`

Change the bean return type from `TaskExecutor` to `AsyncTaskExecutor`:

```java
@Bean
AsyncTaskExecutor bulkFormatTaskExecutor() {
  var executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(1);
  executor.setMaxPoolSize(2);
  executor.setQueueCapacity(10);
  executor.setThreadNamePrefix("bulk-format-");
  executor.initialize();
  return executor;
}
```

Spring matches injection by the bean's declared type, so a service field typed `AsyncTaskExecutor`
would otherwise not find a bean declared as `TaskExecutor`.

### 2. `BulkFormatStatus`

Add `CANCELLED`.

### 3. `BulkFormatService`

- Field: `private final AsyncTaskExecutor bulkFormatTaskExecutor;` (was `TaskExecutor`).
- Field: `private final ConcurrentHashMap<UUID, Future<?>> bulkFormatFutures = new ConcurrentHashMap<>();`

#### `dispatch(BulkFormatEntity job, List<NoteToProcess> notesToProcess)`

```java
var futureRef = new AtomicReference<Future<?>>();
var future = bulkFormatTaskExecutor.submit(
    () -> {
      try {
        processNotes(job, notesToProcess);
      } catch (Exception e) {
        log.error("Unexpected error during bulk format processing. analysisId={}",
            job.getAnalysisId(), e);
      } finally {
        bulkFormatFutures.remove(job.getAnalysisId(), futureRef.get());
      }
    });
futureRef.set(future);
bulkFormatFutures.put(job.getAnalysisId(), future);
```

`ConcurrentHashMap.remove(key, value)` is the atomic conditional removal, so a completed task only
removes its own future. The `AtomicReference` lets the runnable reference the future assigned after
`submit` returns.

The same change applies to the other `dispatch(BulkFormatEntity job)` overload.

#### `processNotes(BulkFormatEntity job, List<NoteToProcess> notesToProcess)`

Task-start guard at the very top of the method (before fetching analysis/note types), and before
transitioning to `IN_PROGRESS`:

```java
var analysisId = job.getAnalysisId();
if (isCancelled(analysisId)) {
  log.info("Bulk format skipped, already cancelled. analysisId={}", analysisId);
  return;
}
int attempts = job.getAttempts() + 1;
job.setStatus(BulkFormatStatus.IN_PROGRESS);
job.setAttempts(attempts);
bulkFormatRepository.save(job);
```

Loop, at the top of each iteration:

```java
if (Thread.currentThread().isInterrupted()) {
  log.info("Bulk format cancelled. analysisId={}", analysisId);
  return;
}
```

When interrupted the method returns without touching `handleProcessNotesResult`, so the `CANCELLED`
status already written by `cancelBulkFormat` stays intact. `isInterrupted()` does not clear the
flag, so a swallowed `InterruptedException` inside `chatService.formatNote` is still observed at
the next boundary.

Helper:

```java
private boolean isCancelled(UUID analysisId) {
  return bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)
      .map(job -> job.getStatus() == BulkFormatStatus.CANCELLED)
      .orElse(false);
}
```

#### `cancelBulkFormat(UUID analysisId)`

```java
public void cancelBulkFormat(UUID analysisId) {
  var job = bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)
      .orElseThrow(() -> new NotFoundException("No bulk format job found. analysisId={}", analysisId));
  if (job.getStatus() == BulkFormatStatus.PENDING
      || job.getStatus() == BulkFormatStatus.IN_PROGRESS
      || job.getStatus() == BulkFormatStatus.WAITING_RETRY) {
    job.setStatus(BulkFormatStatus.CANCELLED);
    bulkFormatRepository.save(job);
  }
  var future = bulkFormatFutures.remove(analysisId);
  if (future != null) {
    future.cancel(true);
  }
}
```

DB write happens first so the task-start guard and cron see `CANCELLED` even if `cancel(true)`
arrives a hair late. Plain read-modify-write; a terminal job (COMPLETED/FAILED/CANCELLED) is not
flipped, but its future (if any) is still cancelled, which is harmless.

### 4. `AnalysisController`

```java
@PostMapping("/{analysisId}/format/cancel")
@ResponseStatus(HttpStatus.ACCEPTED)
public void cancelBulkFormat(@PathVariable UUID analysisId) {
  bulkFormatService.cancelBulkFormat(analysisId);
}
```

### 5. No changes needed

- `BulkFormatCron`: `findAllActive()` only queries the GSI for `IN_PROGRESS` and `WAITING_RETRY`,
  so `CANCELLED` jobs are never auto-resumed.
- `DeckService` active-job guard: `CANCELLED` is not in its active set.
- `BulkFormat` domain, `BulkFormatEntityMapper`, `BulkFormatRestMapper`, `BulkFormatResponse`:
  status serializes by name, so the frontend sees `"CANCELLED"` with no mapping changes.
- `startBulkFormat`: `CANCELLED` is not in the "already in progress" guard, so a fresh run after
  cancel is allowed and overwrites the old record.
- `BulkFormatRepository`: unchanged.

## Race analysis

| Scenario | Outcome |
| --- | --- |
| Cancel while queued (PENDING) | `future.cancel(true)` marks the not-yet-started `FutureTask` cancelled; the worker never runs it |
| Cancel while running (IN_PROGRESS) | Interrupt flag set; loop's `isInterrupted()` check breaks at the next note boundary; DB keeps `CANCELLED` |
| Cancel while WAITING_RETRY | No running future (task removed itself in `finally`); `CANCELLED` written, cron never resumes it |
| Cron resume racing a cancel | Task-start guard reads `CANCELLED` and aborts before writing `IN_PROGRESS` |
| Task dequeues after cancel (start guard) | Guard aborts before processing any note |

Known residual races (accepted):
- Cancel in the microseconds between `submit()` and the registry `put` misses the future. The
  task-start guard still catches it if the task has not transitioned to `IN_PROGRESS`; if it
  already has, the job runs out because no interrupt is delivered. Microsecond window.
- Cooperative interrupts can be swallowed inside `chatService.formatNote`; the check is then
  deferred to the next note boundary. `isInterrupted()` (non-clearing) limits this.

## Error handling

- `cancelBulkFormat` on a missing job → `NotFoundException` (404).
- Unexpected exceptions in the dispatched task are caught and logged by the existing `catch`
  in `dispatch`; behaviour unchanged.

## Testing

Unit tests for `BulkFormatService` with Mockito (available via
`spring-boot-starter-webmvc-test`), plus a real small `ThreadPoolTaskExecutor` where the
interrupt path must actually run:

- `cancelBulkFormat` on an active job: writes `CANCELLED`, calls `future.cancel(true)`, removes
  the entry from the registry.
- `cancelBulkFormat` on a COMPLETED job: no DB status change (idempotent).
- `cancelBulkFormat` on a missing job: `NotFoundException`.
- Task-start guard: repository returns a `CANCELLED` job → `processNotes` aborts without fetching
  analysis/note types, without writing `IN_PROGRESS`, and without calling `formatNote`.
- Loop interrupt: with `formatNote` blocked on a latch, cancel the future; verify the loop stops
  and no terminal status is written (no `save` with `COMPLETED`/`FAILED`/`WAITING_RETRY`).
- Re-start after cancel: `startBulkFormat` does not throw for an existing `CANCELLED` job.
- Controller: `POST /{analysisId}/format/cancel` returns 202 and invokes the service.

Compilation and test execution are skipped by the implementing subagent per AGENTS.md.

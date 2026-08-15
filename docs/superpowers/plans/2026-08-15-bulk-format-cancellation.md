# Bulk Format Cancellation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to cancel a bulk format job, stopping queued and running jobs gracefully while persisting a `CANCELLED` status.

**Architecture:** Switch `BulkFormatService` from fire-and-forget `TaskExecutor.execute(Runnable)` to `AsyncTaskExecutor.submit(Runnable) → Future<?>`, tracking each job's future in a `ConcurrentHashMap<UUID, Future<?>>`. `cancelBulkFormat` persists `CANCELLED` in DynamoDB and calls `future.cancel(true)`. A running task cooperates by checking `Thread.currentThread().isInterrupted()` before each progress save and stopping; a plain status read at task start aborts tasks that begin after a cancel. No repository changes.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Spring `AsyncTaskExecutor`/`ThreadPoolTaskExecutor`, DynamoDB (enhanced client, unchanged), JUnit 5 + Mockito.

## Global Constraints

- No new dependencies.
- `BulkFormatRepository` is NOT modified.
- No per-note DB polling: cancellation is detected only via thread interrupt + one start-time status check.
- The implementing subagent MUST NOT run, fix, or debug the build (`./mvnw compile`, `./mvnw test`, etc.) — the human owns compilation per AGENTS.md. Every "Run:" step below is executed by the human; the subagent reports it as "compilation/test execution skipped per AGENTS.md".
- Never start implementation on `main`; work on `feat/bulk-format-cancellation`.
- Follow the repo's formatting (2-space indent, spotless-style).

---

### Task 1: CANCELLED status + Future-based cancel surface

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatStatus.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/config/BulkFormatConfig.java:5,11-12`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java:21,39,81-107`
- Test: `src/test/java/com/felixkroemer/smort/domain/anki/BulkFormatServiceTest.java` (create)

**Interfaces:**
- Produces: `BulkFormatService.cancelBulkFormat(UUID analysisId)` (public), `AsyncTaskExecutor` injected into `BulkFormatService`, `ConcurrentHashMap<UUID, Future<?>> bulkFormatFutures`, `BulkFormatStatus.CANCELLED`, private helper `boolean isCancelled(UUID analysisId)`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/felixkroemer/smort/domain/anki/BulkFormatServiceTest.java`:

```java
package com.felixkroemer.smort.domain.anki;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.anki.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.AsyncTaskExecutor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkFormatServiceTest {

  @Mock BulkFormatRepository bulkFormatRepository;
  @Mock DerivedNoteRepository derivedNoteRepository;
  @Mock AnkiNoteRepository ankiNoteRepository;
  @Mock AnkiNoteTypeService noteTypeService;
  @Mock AnalysisService analysisService;
  @Mock ChatService chatService;
  @Mock BulkFormatEntityMapper bulkFormatEntityMapper;
  @Mock AsyncTaskExecutor bulkFormatTaskExecutor;

  BulkFormatService bulkFormatService;

  @BeforeEach
  void setUp() {
    bulkFormatService =
        new BulkFormatService(
            bulkFormatRepository,
            derivedNoteRepository,
            ankiNoteRepository,
            noteTypeService,
            analysisService,
            chatService,
            bulkFormatEntityMapper,
            bulkFormatTaskExecutor);
  }

  private Analysis analysis() {
    var analysis = new Analysis();
    analysis.setDeckId(1L);
    return analysis;
  }

  private AnkiNoteEntity note(Long id, Long noteTypeId) {
    var note = mock(AnkiNoteEntity.class);
    when(note.getId()).thenReturn(id);
    when(note.getNoteTypeId()).thenReturn(noteTypeId);
    when(note.getFlds()).thenReturn(List.of("front", "back"));
    return note;
  }

  private void stubInlineExecutor() {
    when(bulkFormatTaskExecutor.submit(any(Runnable.class)))
        .thenAnswer(
            inv -> {
              Runnable r = inv.getArgument(0);
              r.run();
              return mock(Future.class);
            });
  }

  @Test
  void cancelBulkFormatWritesCancelledAndCancelsTrackedFuture() {
    var analysisId = UUID.randomUUID();
    var future = mock(Future.class);
    when(bulkFormatTaskExecutor.submit(any(Runnable.class))).thenReturn(future);
    var inProgressJob = new BulkFormatEntity(analysisId, true);
    inProgressJob.setStatus(BulkFormatStatus.IN_PROGRESS);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.empty(), Optional.of(inProgressJob));
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L, 100L)));
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());

    bulkFormatService.startBulkFormat(analysisId, true);
    bulkFormatService.cancelBulkFormat(analysisId);
    bulkFormatService.cancelBulkFormat(analysisId);

    verify(bulkFormatRepository)
        .save(argThat(job -> job.getStatus() == BulkFormatStatus.CANCELLED));
    verify(future, times(1)).cancel(anyBoolean());
  }

  @Test
  void cancelBulkFormatOnCompletedJobIsNoop() {
    var analysisId = UUID.randomUUID();
    var completedJob = new BulkFormatEntity(analysisId, true);
    completedJob.setStatus(BulkFormatStatus.COMPLETED);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.of(completedJob));

    bulkFormatService.cancelBulkFormat(analysisId);

    verify(bulkFormatRepository, never()).save(any());
  }

  @Test
  void cancelBulkFormatOnMissingJobThrowsNotFoundException() {
    when(bulkFormatRepository.findBulkFormatByAnalysisId(any())).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class, () -> bulkFormatService.cancelBulkFormat(UUID.randomUUID()));
  }

  @Test
  void canRestartAfterCancel() {
    when(bulkFormatTaskExecutor.submit(any(Runnable.class))).thenReturn(mock(Future.class));
    var analysisId = UUID.randomUUID();
    var cancelledJob = new BulkFormatEntity(analysisId, true);
    cancelledJob.setStatus(BulkFormatStatus.CANCELLED);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.of(cancelledJob));
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L, 100L)));
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());

    assertDoesNotThrow(() -> bulkFormatService.startBulkFormat(analysisId, true));

    verify(bulkFormatRepository).save(argThat(job -> job.getStatus() == BulkFormatStatus.PENDING));
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=BulkFormatServiceTest`
Expected: FAIL — `cancelBulkFormat`, `bulkFormatFutures`, `isCancelled` do not exist yet (compile errors); `BulkFormatStatus.CANCELLED` missing.

- [ ] **Step 3: Add `CANCELLED` to `BulkFormatStatus`**

```java
public enum BulkFormatStatus {
  PENDING,
  IN_PROGRESS,
  WAITING_RETRY,
  COMPLETED,
  FAILED,
  CANCELLED
}
```

- [ ] **Step 4: Change the executor bean type in `BulkFormatConfig`**

In `src/main/java/com/felixkroemer/smort/infrastructure/config/BulkFormatConfig.java`, change the import `org.springframework.core.task.TaskExecutor` to `org.springframework.core.task.AsyncTaskExecutor` and change the bean signature:

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

- [ ] **Step 5: Update imports and field in `BulkFormatService`**

Replace `import org.springframework.core.task.TaskExecutor;` with:

```java
import org.springframework.core.task.AsyncTaskExecutor;
```

Add to the import block (alphabetical, near the other `java.util.concurrent` imports):

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
```

Change the field and add the registry after it:

```java
  private final AsyncTaskExecutor bulkFormatTaskExecutor;
  private final ConcurrentHashMap<UUID, Future<?>> bulkFormatFutures = new ConcurrentHashMap<>();
```

- [ ] **Step 6: Replace both `dispatch` methods with submit + registry**

Replace the two existing `dispatch` methods (currently calling `bulkFormatTaskExecutor.execute(...)`) and the `resumeBulkFormat`-adjacent block with:

```java
  private void dispatch(BulkFormatEntity job) {
    submitAndTrack(job.getAnalysisId(), () -> processNotes(job));
  }

  private void dispatch(BulkFormatEntity job, List<NoteToProcess> notesToProcess) {
    submitAndTrack(job.getAnalysisId(), () -> processNotes(job, notesToProcess));
  }

  private void submitAndTrack(UUID analysisId, Runnable task) {
    var futureRef = new AtomicReference<Future<?>>();
    var future =
        bulkFormatTaskExecutor.submit(
            () -> {
              try {
                task.run();
              } catch (Exception e) {
                log.error(
                    "Unexpected error during bulk format processing. analysisId={}", analysisId, e);
              } finally {
                bulkFormatFutures.remove(analysisId, futureRef.get());
              }
            });
    futureRef.set(future);
    bulkFormatFutures.put(analysisId, future);
  }
```

- [ ] **Step 7: Add `cancelBulkFormat` and `isCancelled`**

Insert after `resumeBulkFormat`:

```java
  public void cancelBulkFormat(UUID analysisId) {
    var job =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "No bulk format job found. analysisId={}", analysisId));
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

  private boolean isCancelled(UUID analysisId) {
    return bulkFormatRepository
        .findBulkFormatByAnalysisId(analysisId)
        .map(job -> job.getStatus() == BulkFormatStatus.CANCELLED)
        .orElse(false);
  }
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./mvnw test -Dtest=BulkFormatServiceTest`
Expected: PASS (4 tests). (Skipped by implementing subagent per AGENTS.md.)

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatStatus.java \
        src/main/java/com/felixkroemer/smort/infrastructure/config/BulkFormatConfig.java \
        src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java \
        src/test/java/com/felixkroemer/smort/domain/anki/BulkFormatServiceTest.java
git commit -m "feat: add Future-based cancellation surface for bulk format jobs"
```

---

### Task 2: Cooperative stop — task-start guard and interrupt check

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java:109-209`
- Test: `src/test/java/com/felixkroemer/smort/domain/anki/BulkFormatServiceTest.java`

**Interfaces:**
- Consumes: `isCancelled(UUID)` (private, from Task 1), `BulkFormatStatus.CANCELLED`.
- Produces: modified private `processNotes(BulkFormatEntity)` and `processNotes(BulkFormatEntity, List<NoteToProcess>)` that abort on cancel.

- [ ] **Step 1: Add the failing tests**

Append to `BulkFormatServiceTest.java`. Add these imports:

```java
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
```

Update the `analysis()` helper to set format instructions:

```java
  private Analysis analysis() {
    var analysis = new Analysis();
    analysis.setDeckId(1L);
    analysis.setFormatInstructions(Optional.of("instructions"));
    return analysis;
  }
```

Add the tests:

```java
  @Test
  void taskStartGuardAbortsCancelledJob() {
    stubInlineExecutor();
    var analysisId = UUID.randomUUID();
    var cancelledJob = new BulkFormatEntity(analysisId, true);
    cancelledJob.setStatus(BulkFormatStatus.CANCELLED);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.of(cancelledJob));

    bulkFormatService.resumeBulkFormat(cancelledJob);

    verify(analysisService, never()).getAnalysis(any());
    verify(noteTypeService, never()).getNoteTypesByAnalysisId(any());
    verify(chatService, never()).formatNote(any(), any());
    verify(bulkFormatRepository, never()).save(any());
  }

  @Test
  void interruptStopsLoopWithoutWritingTerminalStatus() {
    stubInlineExecutor();
    var analysisId = UUID.randomUUID();
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.empty(), Optional.of(new BulkFormatEntity(analysisId, true)));
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L, 100L), note(2L, 100L)));
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());
    var noteType = mock(AnkiNoteTypeEntity.class);
    when(noteType.getFields()).thenReturn(List.of("front", "back"));
    when(noteTypeService.getNoteTypesByAnalysisId(analysisId)).thenReturn(java.util.Map.of(100L, noteType));
    when(chatService.formatNote(any(), any())).thenReturn(new NoteSchema("f2", "b2"));

    Thread.currentThread().interrupt();
    try {
      bulkFormatService.startBulkFormat(analysisId, true);
    } finally {
      Thread.interrupted();
    }

    verify(chatService, times(1)).formatNote(any(), any());
    verify(derivedNoteRepository, times(1)).save(any());
    verify(bulkFormatRepository, never())
        .save(
            argThat(
                job ->
                    job.getStatus() == BulkFormatStatus.COMPLETED
                        || job.getStatus() == BulkFormatStatus.FAILED
                        || job.getStatus() == BulkFormatStatus.WAITING_RETRY));
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=BulkFormatServiceTest`
Expected: FAIL — the guard and interrupt check are not implemented yet. `taskStartGuardAbortsCancelledJob` calls `getAnalysis`, and `interruptStopsLoopWithoutWritingTerminalStatus` finishes all notes and writes `COMPLETED`.

- [ ] **Step 3: Add the start guard to the resume overload**

`processNotes(BulkFormatEntity job)` (the overload that re-derives notes) already begins with
`var analysisId = job.getAnalysisId();` followed by the analysis fetch. Insert the guard between
them, so the method aborts before any repository fetches:

```java
    var analysisId = job.getAnalysisId();
    if (isCancelled(analysisId)) {
      log.info("Bulk format skipped, already cancelled. analysisId={}", analysisId);
      return;
    }
```

- [ ] **Step 4: Add the start guard and interrupt check to the processing method**

`processNotes(BulkFormatEntity job, List<NoteToProcess> notesToProcess)` also already begins with
`var analysisId = job.getAnalysisId();` followed by the analysis fetch. Insert the same guard
between them (do NOT redeclare `analysisId`):

```java
    var analysisId = job.getAnalysisId();
    if (isCancelled(analysisId)) {
      log.info("Bulk format skipped, already cancelled. analysisId={}", analysisId);
      return;
    }
```

Then restructure the note loop. The current try block does the progress save inside `try`:

```java
      derivedNoteRepository.save(derivedNote);

      processed++;
      consecutiveFailed = 0;
      job.setCompletedNotes(job.getCompletedNotes() + 1);
      job.setLastUpdatedAt(Instant.now());
      bulkFormatRepository.save(job);
```

Replace the tail of the try block and the catch block so the progress save moves out of the try, the failure path `continue`s, and an interrupt stops the loop before saving:

```java
      derivedNoteRepository.save(derivedNote);

      processed++;
      consecutiveFailed = 0;
      job.setCompletedNotes(job.getCompletedNotes() + 1);

    } catch (Exception e) {
      failed++;
      consecutiveFailed++;
      log.warn(
          "Failed to format note during bulk format. analysisId={}, noteId={}",
          analysisId,
          noteEntity.getId(),
          e);
      if (consecutiveFailed >= MAX_RECENT_FAILED) {
        log.warn(
            "Hit consecutive failed limit while processing bulk format. analysisId={}",
            analysisId);
        break;
      }
      continue;
    }

    if (Thread.currentThread().isInterrupted()) {
      log.info("Bulk format cancelled. analysisId={}", analysisId);
      return;
    }
    job.setLastUpdatedAt(Instant.now());
    bulkFormatRepository.save(job);
  }
```

The result is: each successful note saves its progress only if the thread is not interrupted; an interrupt at any point returns immediately without calling `handleProcessNotesResult`, leaving the `CANCELLED` status (written by `cancelBulkFormat`) untouched.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw test -Dtest=BulkFormatServiceTest`
Expected: PASS (6 tests). (Skipped by implementing subagent per AGENTS.md.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java \
        src/test/java/com/felixkroemer/smort/domain/anki/BulkFormatServiceTest.java
git commit -m "feat: stop bulk format jobs cooperatively on interrupt and cancelled start"
```

---

### Task 3: Cancel REST endpoint

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java` (after the `format/status` GET, line 219)
- Test: `src/test/java/com/felixkroemer/smort/application/anki/AnalysisControllerTest.java` (create)

**Interfaces:**
- Consumes: `BulkFormatService.cancelBulkFormat(UUID analysisId)`.
- Produces: `POST /analysis/{analysisId}/format/cancel` returning `202 Accepted`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/felixkroemer/smort/application/anki/AnalysisControllerTest.java`:

```java
package com.felixkroemer.smort.application.anki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.felixkroemer.smort.application.anki.mapping.AnalysisRestMapper;
import com.felixkroemer.smort.application.anki.mapping.AnkiNoteRestMapper;
import com.felixkroemer.smort.application.anki.mapping.BulkFormatRestMapper;
import com.felixkroemer.smort.application.anki.mapping.ChatMessageRestMapper;
import com.felixkroemer.smort.application.chat.mapping.ChatMessageRestMapper;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.domain.anki.AnkiNoteService;
import com.felixkroemer.smort.domain.anki.AnkiNoteTypeService;
import com.felixkroemer.smort.domain.anki.BulkFormatService;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

class AnalysisControllerTest {

  @Test
  void cancelBulkFormatInvokesServiceAndReturns202() throws Exception {
    var bulkFormatService = mock(BulkFormatService.class);
    var controller =
        new AnalysisController(
            mock(AnalysisService.class),
            mock(AnkiNoteService.class),
            mock(ChatOrchestrationService.class),
            mock(AnkiNoteTypeService.class),
            bulkFormatService,
            mock(AnalysisRestMapper.class),
            mock(AnkiNoteRestMapper.class),
            mock(BulkFormatRestMapper.class),
            mock(ChatMessageRestMapper.class));

    var method = AnalysisController.class.getMethod("cancelBulkFormat", UUID.class);
    var responseStatus = method.getAnnotation(ResponseStatus.class);
    assertEquals(HttpStatus.ACCEPTED, responseStatus.value());

    var analysisId = UUID.randomUUID();
    controller.cancelBulkFormat(analysisId);
    verify(bulkFormatService).cancelBulkFormat(analysisId);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AnalysisControllerTest`
Expected: FAIL — `AnalysisController.cancelBulkFormat` does not exist yet (compile error).

- [ ] **Step 3: Add the endpoint**

In `AnalysisController`, after the `getBulkFormatStatus` GET method:

```java
  @PostMapping("/{analysisId}/format/cancel")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void cancelBulkFormat(@PathVariable UUID analysisId) {
    bulkFormatService.cancelBulkFormat(analysisId);
  }
```

(`@PostMapping`, `@PathVariable`, `@ResponseStatus`, and `HttpStatus` are already imported via the wildcard `org.springframework.web.bind.annotation.*` and `org.springframework.http.HttpStatus`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AnalysisControllerTest`
Expected: PASS. (Skipped by implementing subagent per AGENTS.md.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java \
        src/test/java/com/felixkroemer/smort/application/anki/AnalysisControllerTest.java
git commit -m "feat: add bulk format cancel endpoint"
```

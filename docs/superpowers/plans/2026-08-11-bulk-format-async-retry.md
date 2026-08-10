# Async Bulk Format with `WAITING_RETRY` State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make bulk format responsive (POST returns immediately, processing runs on a background executor) and make partial failures visible (new `WAITING_RETRY` status, `failedCount` and `attempts` in the polling response).

**Architecture:** `BulkFormatService.startBulkFormat`/`resumeBulkFormat` save job state then dispatch `processNotes` to a hardcoded `ThreadPoolTaskExecutor`; the job entity gains `failedCount` and a `WAITING_RETRY` status; `BulkFormatRepository.findAllActive()` queries the status GSI for both `IN_PROGRESS` and `WAITING_RETRY`; `BulkFormatCron` resumes jobs idle > 2 minutes exactly as today.

**Tech Stack:** Java 25, Spring Boot 4.0.3, DynamoDB Enhanced SDK, Lombok, JUnit 5 + Mockito 5.

## Global Constraints

- DynamoDB table: `common-table` (single-table design); entities use `@DynamoDbBean` + Lombok `@Getter/@Setter/@NoArgsConstructor`
- Partition key format for analysis items: `ANALYSIS#<UUID>`; sort key constants live in `keys/sort/` utility classes
- No concurrency guards added — only the existing DB-status guards
- Executor sizing is hardcoded (core 1 / max 2 / queue 10) — **not** configurable via properties
- Code style: 2-space indent, same conventions as surrounding files
- **Environment limitation:** this checkout is on JDK 21 while the project targets Java 25, so `./mvnw compile`/`test` cannot run locally (pre-existing). Test/compile gates in this plan are to be run in a JDK-25 environment; locally, verify by careful review.
- `DeckService.java` has uncommitted local changes (null-guards around `handleDerivedNotes`/`handleUnmappedNotes`). Keep them; only add the new guard at the top of `importDeck`.

## File Structure

**Created:**
| File | Responsibility |
|---|---|
| `infrastructure/config/BulkFormatConfig.java` | Hardcoded `TaskExecutor` bean |
| `src/test/java/.../domain/anki/BulkFormatServiceTest.java` | Service behavior: dispatch, retry states, failedCount |
| `src/test/java/.../infrastructure/dynamodb/anki/BulkFormatRepositoryTest.java` | `findAllActive()` combines both statuses |
| `src/test/java/.../domain/cron/BulkFormatCronTest.java` | Cron resumes idle active jobs |

**Modified:**
| File | Change |
|---|---|
| `infrastructure/dynamodb/anki/BulkFormatStatus.java` | Add `WAITING_RETRY` |
| `infrastructure/dynamodb/anki/BulkFormatEntity.java` | Add `failedCount` |
| `domain/anki/BulkFormat.java` | Add `failedCount`, `attempts` |
| `application/anki/dto/BulkFormatStatusResponse.java` | Add `failedCount`, `attempts` |
| `application/anki/AnalysisController.java` | Pass new fields in status response |
| `infrastructure/dynamodb/anki/BulkFormatRepository.java` | `findAllInProgress()` → `findAllActive()` (two-status query) |
| `domain/cron/BulkFormatCron.java` | Use `findAllActive()` |
| `domain/anki/BulkFormatService.java` | Dispatch via executor; `WAITING_RETRY` transition; persist `failedCount` |
| `domain/deck/DeckService.java` | Import guard includes `WAITING_RETRY` |

## Task 1: `WAITING_RETRY` status + `failedCount`/`attempts` data model

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatStatus.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatEntity.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormat.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/dto/BulkFormatStatusResponse.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java`

**Interfaces:**
- Consumes: existing `BulkFormatStatus`, `BulkFormatEntity`, `BulkFormat`, `BulkFormatStatusResponse` definitions
- Produces: `BulkFormatStatus.WAITING_RETRY`; `BulkFormatEntity.getFailedCount()/setFailedCount(int)`; `BulkFormat.getFailedCount()/setFailedCount(int)` and `getAttempts()/setAttempts(int)`; `BulkFormatStatusResponse(String status, Instant createdAt, Instant lastUpdatedAt, int totalNotes, int completedNotes, int failedCount, int attempts)`

This is mechanical field addition (no behavior), verified by compile in a JDK-25 environment. `BulkFormatMapper` maps the new fields by name — no mapper change.

- [ ] **Step 1: Add `WAITING_RETRY` to `BulkFormatStatus`**

```java
public enum BulkFormatStatus {
  PENDING,
  IN_PROGRESS,
  WAITING_RETRY,
  COMPLETED,
  FAILED
}
```

- [ ] **Step 2: Add `failedCount` to `BulkFormatEntity`**

After the `private int attempts;` field, add:

```java
    private int failedCount;
```

- [ ] **Step 3: Add `failedCount` and `attempts` to domain `BulkFormat`**

After `private int completedNotes;`, add:

```java
  private int failedCount;
  private int attempts;
```

- [ ] **Step 4: Extend `BulkFormatStatusResponse`**

```java
public record BulkFormatStatusResponse(
    String status, Instant createdAt, Instant lastUpdatedAt, int totalNotes, int completedNotes,
    int failedCount, int attempts) {}
```

- [ ] **Step 5: Pass the new fields in `AnalysisController.getBulkFormatStatus`**

Replace the current `new BulkFormatStatusResponse(...)` body with:

```java
    return new BulkFormatStatusResponse(
        job.getStatus().name(), job.getCreatedAt(), job.getLastUpdatedAt(),
        job.getTotalNotes(), job.getCompletedNotes(), job.getFailedCount(), job.getAttempts());
```

- [ ] **Step 6: Verify compile**

Run: `./mvnw compile`
Expected: clean compile in a JDK-25 environment (fails locally on this JDK-21 checkout — pre-existing, not caused by this change).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatStatus.java \
        src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatEntity.java \
        src/main/java/com/felixkroemer/smort/domain/anki/BulkFormat.java \
        src/main/java/com/felixkroemer/smort/application/anki/dto/BulkFormatStatusResponse.java \
        src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java
git commit -m "feat: add WAITING_RETRY status and failedCount/attempts to bulk format model"
```

---

## Task 2: `findAllActive()` repository query + cron switch

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepository.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/cron/BulkFormatCron.java`
- Test: `src/test/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepositoryTest.java`
- Test: `src/test/java/com/felixkroemer/smort/domain/cron/BulkFormatCronTest.java`

**Interfaces:**
- Consumes: `BulkFormatStatus.IN_PROGRESS`, `BulkFormatStatus.WAITING_RETRY` (from Task 1); `BulkFormatEntity`
- Produces: `BulkFormatRepository.findAllActive()` → `List<BulkFormatEntity>` (both active statuses); `BulkFormatCron.resumeCrashedBulkFormats()` unchanged signature

- [ ] **Step 1: Write the failing repository test**

Create `src/test/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepositoryTest.java`:

```java
package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

class BulkFormatRepositoryTest {

  private DynamoDbIndex<BulkFormatEntity> statusBulkFormatIndex;
  private BulkFormatRepository repository;

  @BeforeEach
  void setUp() {
    DynamoDbTable<BulkFormatEntity> table = mock(DynamoDbTable.class);
    statusBulkFormatIndex = mock(DynamoDbIndex.class);
    repository = new BulkFormatRepository(table, statusBulkFormatIndex);
  }

  @Test
  void findAllActiveCombinesInProgressAndWaitingRetry() {
    var inProgress = new BulkFormatEntity(UUID.randomUUID());
    inProgress.setStatus(BulkFormatStatus.IN_PROGRESS);
    var waitingRetry = new BulkFormatEntity(UUID.randomUUID());
    waitingRetry.setStatus(BulkFormatStatus.WAITING_RETRY);

    when(statusBulkFormatIndex.query(any(QueryEnhancedRequest.class)))
        .thenReturn(pageOf(inProgress), pageOf(waitingRetry));

    var result = repository.findAllActive();

    assertThat(result).containsExactlyInAnyOrder(inProgress, waitingRetry);
    verify(statusBulkFormatIndex, times(2)).query(any(QueryEnhancedRequest.class));
  }

  private PageIterable<BulkFormatEntity> pageOf(BulkFormatEntity... items) {
    Page<BulkFormatEntity> page = Page.create(List.of(items), null, null);
    return () -> List.of(page).iterator();
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=BulkFormatRepositoryTest`
Expected: FAIL — `findAllActive()` not found (only `findAllInProgress()` exists).

- [ ] **Step 3: Implement `findAllActive()` in `BulkFormatRepository`**

Replace the `findAllInProgress()` method with:

```java
  public List<BulkFormatEntity> findAllActive() {
    return Stream.of(BulkFormatStatus.IN_PROGRESS, BulkFormatStatus.WAITING_RETRY)
        .flatMap(
            status ->
                statusBulkFormatIndex
                    .query(
                        QueryEnhancedRequest.builder()
                            .queryConditional(
                                QueryConditional.keyEqualTo(
                                    Key.builder().partitionValue(status.name()).build()))
                            .build())
                    .stream()
                    .flatMap(page -> page.items().stream()))
        .toList();
  }
```

Add `import java.util.stream.Stream;` to the imports.

- [ ] **Step 4: Run the repository test to verify it passes**

Run: `./mvnw test -Dtest=BulkFormatRepositoryTest`
Expected: PASS.

- [ ] **Step 5: Write the failing cron test**

Create `src/test/java/com/felixkroemer/smort/domain/cron/BulkFormatCronTest.java`:

```java
package com.felixkroemer.smort.domain.cron;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.felixkroemer.smort.domain.anki.BulkFormatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BulkFormatCronTest {

  private BulkFormatRepository bulkFormatRepository;
  private BulkFormatService bulkFormatService;
  private BulkFormatCron cron;

  @BeforeEach
  void setUp() {
    bulkFormatRepository = mock(BulkFormatRepository.class);
    bulkFormatService = mock(BulkFormatService.class);
    cron = new BulkFormatCron(bulkFormatRepository, bulkFormatService);
  }

  @Test
  void resumesActiveJobIdlePastTimeout() {
    var analysisId = UUID.randomUUID();
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.WAITING_RETRY);
    job.setLastUpdatedAt(Instant.now().minus(Duration.ofMinutes(5)));

    when(bulkFormatRepository.findAllActive()).thenReturn(List.of(job));

    cron.resumeCrashedBulkFormats();

    verify(bulkFormatService).resumeBulkFormat(analysisId);
  }

  @Test
  void skipsActiveJobUpdatedRecently() {
    var analysisId = UUID.randomUUID();
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setLastUpdatedAt(Instant.now());

    when(bulkFormatRepository.findAllActive()).thenReturn(List.of(job));

    cron.resumeCrashedBulkFormats();

    verify(bulkFormatService, never()).resumeBulkFormat(any());
  }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./mvnw test -Dtest=BulkFormatCronTest`
Expected: FAIL — `findAllActive()` not found (cron still calls `findAllInProgress()`).

- [ ] **Step 7: Switch `BulkFormatCron` to `findAllActive`**

In `BulkFormatCron.resumeCrashedBulkFormats()`, replace `bulkFormatRepository.findAllInProgress()` with `bulkFormatRepository.findAllActive()`.

- [ ] **Step 8: Run the cron test to verify it passes**

Run: `./mvnw test -Dtest=BulkFormatCronTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepository.java \
        src/main/java/com/felixkroemer/smort/domain/cron/BulkFormatCron.java \
        src/test/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepositoryTest.java \
        src/test/java/com/felixkroemer/smort/domain/cron/BulkFormatCronTest.java
git commit -m "feat: resume bulk format jobs in WAITING_RETRY state via findAllActive"
```

---

## Task 3: Executor bean + async service + `WAITING_RETRY` transition

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/config/BulkFormatConfig.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java`
- Test: `src/test/java/com/felixkroemer/smort/domain/anki/BulkFormatServiceTest.java`

**Interfaces:**
- Consumes: `BulkFormatStatus.WAITING_RETRY`, `BulkFormatEntity.setFailedCount` (Task 1); `BulkFormatRepository`, `DerivedNoteRepository`, `AnkiNoteRepository`, `AnkiNoteTypeService`, `AnalysisService`, `ChatService`, `BulkFormatMapper`, `TaskExecutor`
- Produces: bean `bulkFormatTaskExecutor`; `BulkFormatService` constructor gains a final `TaskExecutor` param (8th); `startBulkFormat(UUID)` and `resumeBulkFormat(UUID)` now dispatch processing instead of running it inline; `processNotes` persists `failedCount` and sets `WAITING_RETRY` on partial failure
- Reference: `Analysis` (`getDeckId()`), `AnkiNoteEntity` (`getId()`, `getNoteTypeId()`, `getFlds()`), `AnkiNoteTypeEntity` (`getFields()`), `ChatService.NoteSchema` (public fields `front`, `back`), `DerivedNoteEntity(analysisId, noteId, front, back)`

- [ ] **Step 1: Write the failing service test**

Create `src/test/java/com/felixkroemer/smort/domain/anki/BulkFormatServiceTest.java`:

```java
package com.felixkroemer.smort.domain.anki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.mapping.BulkFormatMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

class BulkFormatServiceTest {

  private final UUID analysisId = UUID.randomUUID();
  private BulkFormatRepository bulkFormatRepository;
  private DerivedNoteRepository derivedNoteRepository;
  private AnkiNoteRepository ankiNoteRepository;
  private AnkiNoteTypeService noteTypeService;
  private AnalysisService analysisService;
  private ChatService chatService;
  private BulkFormatMapper bulkFormatMapper;
  private TaskExecutor taskExecutor;
  private BulkFormatService service;

  @BeforeEach
  void setUp() {
    bulkFormatRepository = mock(BulkFormatRepository.class);
    derivedNoteRepository = mock(DerivedNoteRepository.class);
    ankiNoteRepository = mock(AnkiNoteRepository.class);
    noteTypeService = mock(AnkiNoteTypeService.class);
    analysisService = mock(AnalysisService.class);
    chatService = mock(ChatService.class);
    bulkFormatMapper = mock(BulkFormatMapper.class);
    taskExecutor = mock(TaskExecutor.class);
    service =
        new BulkFormatService(
            bulkFormatRepository,
            derivedNoteRepository,
            ankiNoteRepository,
            noteTypeService,
            analysisService,
            chatService,
            bulkFormatMapper,
            taskExecutor);
  }

  /** Makes the mocked TaskExecutor run dispatched Runnables inline. */
  private void runTasksSynchronously() {
    doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return null;
            })
        .when(taskExecutor)
        .execute(any(Runnable.class));
  }

  private Analysis analysis() {
    var analysis = new Analysis();
    analysis.setAnalysisId(analysisId);
    analysis.setDeckId(1L);
    return analysis;
  }

  private AnkiNoteEntity note(long id) {
    var note = mock(AnkiNoteEntity.class);
    when(note.getId()).thenReturn(id);
    when(note.getNoteTypeId()).thenReturn(1L);
    when(note.getFlds()).thenReturn(List.of("field-a", "field-b"));
    return note;
  }

  private AnkiNoteTypeEntity noteType() {
    var noteType = mock(AnkiNoteTypeEntity.class);
    when(noteType.getFields()).thenReturn(List.of("field-a", "field-b"));
    return noteType;
  }

  private void stubFormatNoteSuccess() {
    when(chatService.formatNote(any()))
        .thenAnswer(
            i -> {
              var schema = new ChatService.NoteSchema();
              schema.front = "front";
              schema.back = "back";
              return schema;
            });
  }

  private void stubFormatNoteFailure() {
    when(chatService.formatNote(any())).thenThrow(new SmortException("model refused"));
  }

  private BulkFormatEntity lastSavedJob() {
    ArgumentCaptor<BulkFormatEntity> captor = ArgumentCaptor.forClass(BulkFormatEntity.class);
    verify(bulkFormatRepository, atLeastOnce()).save(captor.capture());
    List<BulkFormatEntity> saved = captor.getAllValues();
    return saved.get(saved.size() - 1);
  }

  private void stubStandardJobSetup() {
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L), note(2L)));
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());
    when(noteTypeService.getNoteTypesByAnalysisId(analysisId)).thenReturn(Map.of(1L, noteType()));
  }

  @Test
  void startBulkFormatSchedulesProcessingWithoutRunningIt() {
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.empty());
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L)));

    service.startBulkFormat(analysisId);

    verify(taskExecutor).execute(any(Runnable.class));
    verify(chatService, never()).formatNote(any());
    verify(bulkFormatRepository)
        .save(argThat(job -> job.getStatus() == BulkFormatStatus.IN_PROGRESS));
  }

  @Test
  void startBulkFormatCompletesWhenAllNotesSucceed() {
    runTasksSynchronously();
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.empty());
    stubStandardJobSetup();
    stubFormatNoteSuccess();

    service.startBulkFormat(analysisId);

    var job = lastSavedJob();
    assertThat(job.getStatus()).isEqualTo(BulkFormatStatus.COMPLETED);
    assertThat(job.getCompletedNotes()).isEqualTo(2);
    assertThat(job.getFailedCount()).isZero();
    assertThat(job.getAttempts()).isEqualTo(1);
    verify(derivedNoteRepository, times(2)).save(any(DerivedNoteEntity.class));
  }

  @Test
  void startBulkFormatSetsWaitingRetryOnPartialFailure() {
    runTasksSynchronously();
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.empty());
    stubStandardJobSetup();
    stubFormatNoteFailure();

    service.startBulkFormat(analysisId);

    var job = lastSavedJob();
    assertThat(job.getStatus()).isEqualTo(BulkFormatStatus.WAITING_RETRY);
    assertThat(job.getFailedCount()).isEqualTo(2);
    assertThat(job.getAttempts()).isEqualTo(1);
    verify(derivedNoteRepository, never()).save(any(DerivedNoteEntity.class));
  }

  @Test
  void resumeBulkFormatExhaustsAttemptsToFailed() {
    runTasksSynchronously();
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.WAITING_RETRY);
    job.setAttempts(1);
    job.setTotalNotes(2);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.of(job));
    stubStandardJobSetup();
    stubFormatNoteFailure();

    service.resumeBulkFormat(analysisId);

    var finalJob = lastSavedJob();
    assertThat(finalJob.getStatus()).isEqualTo(BulkFormatStatus.FAILED);
    assertThat(finalJob.getAttempts()).isEqualTo(2);
    assertThat(finalJob.getFailedCount()).isEqualTo(2);
  }

  @Test
  void resumeBulkFormatSetsInProgressBeforeDispatching() {
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.WAITING_RETRY);
    job.setAttempts(1);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.of(job));

    service.resumeBulkFormat(analysisId);

    verify(bulkFormatRepository)
        .save(argThat(saved -> saved.getStatus() == BulkFormatStatus.IN_PROGRESS));
    verify(taskExecutor).execute(any(Runnable.class));
  }

  @Test
  void startBulkFormatRejectsWaitingRetryJob() {
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.WAITING_RETRY);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> service.startBulkFormat(analysisId))
        .isInstanceOf(SmortException.class);
    verify(taskExecutor, never()).execute(any(Runnable.class));
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=BulkFormatServiceTest`
Expected: FAIL — compile error: `BulkFormatService` constructor takes 7 args, not 8 (`TaskExecutor` not yet a field).

- [ ] **Step 3: Create the `TaskExecutor` bean**

Create `src/main/java/com/felixkroemer/smort/infrastructure/config/BulkFormatConfig.java`:

```java
package com.felixkroemer.smort.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BulkFormatConfig {

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
}
```

- [ ] **Step 4: Add the `TaskExecutor` dependency to `BulkFormatService`**

Add the field after `bulkFormatMapper`:

```java
  private final TaskExecutor bulkFormatTaskExecutor;
```

Add `import org.springframework.core.task.TaskExecutor;`. (`java.time.Instant` and `java.util.UUID` are already imported.)

- [ ] **Step 5: Dispatch processing from `startBulkFormat` and `resumeBulkFormat`**

Replace `startBulkFormat` with:

```java
  public void startBulkFormat(UUID analysisId) {
    var existing = bulkFormatRepository.findBulkFormatByAnalysisId(analysisId);
    if (existing.isPresent()) {
      var job = existing.get();
      if (job.getStatus() == BulkFormatStatus.IN_PROGRESS
          || job.getStatus() == BulkFormatStatus.PENDING
          || job.getStatus() == BulkFormatStatus.WAITING_RETRY) {
        throw new SmortException(
            "Bulk format already in progress for analysis. analysisId={}", analysisId);
      }
      if (job.getStatus() == BulkFormatStatus.COMPLETED) {
        throw new SmortException(
            "Bulk format already completed for analysis. analysisId={}", analysisId);
      }
    }

    var analysis = analysisService.getAnalysis(analysisId);
    var totalNotes =
        ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId()).size();

    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setTotalNotes(totalNotes);
    bulkFormatRepository.save(job);

    dispatch(analysisId, job);
  }
```

Replace `resumeBulkFormat` with:

```java
  public void resumeBulkFormat(UUID analysisId) {
    var job =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .orElseThrow(
                () -> new SmortException("No bulk format job found. analysisId={}", analysisId));

    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setLastUpdatedAt(Instant.now());
    bulkFormatRepository.save(job);

    dispatch(analysisId, job);
  }
```

Add the private dispatcher (place it between `resumeBulkFormat` and `processNotes`):

```java
  private void dispatch(UUID analysisId, BulkFormatEntity job) {
    bulkFormatTaskExecutor.execute(
        () -> {
          try {
            processNotes(analysisId, job);
          } catch (Exception e) {
            log.error("Unexpected error during bulk format processing. analysisId={}", analysisId, e);
          }
        });
  }
```

- [ ] **Step 6: Persist `failedCount` and set `WAITING_RETRY` in `processNotes`**

In `processNotes`, after `job.setTotalNotes(notes.size());` add `job.setFailedCount(0);`.

In the success path, after `job.setLastUpdatedAt(Instant.now());` add `job.setFailedCount(failed);`.

In the catch block, after `failed++;` and `consecutiveFailed++;`, add `job.setFailedCount(failed);`.

In the `if (failed == 0)` branch, after `job.setStatus(BulkFormatStatus.COMPLETED);` add `job.setFailedCount(0);`.

Replace the final else branch with:

```java
      } else {
        job.setStatus(BulkFormatStatus.WAITING_RETRY);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);

        log.info(
            "Bulk format had errors. Will resume later.  analysisId={}, processed={}, failed={}, attempts={}",
            analysisId,
            processed,
            failed,
            attempts);
      }
```

(The existing `if (job.getAttempts() >= MAX_ATTEMPTS)` → `FAILED` branch is unchanged.)

- [ ] **Step 7: Run the service test to verify it passes**

Run: `./mvnw test -Dtest=BulkFormatServiceTest`
Expected: PASS (all 6 tests).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/config/BulkFormatConfig.java \
        src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java \
        src/test/java/com/felixkroemer/smort/domain/anki/BulkFormatServiceTest.java
git commit -m "feat: run bulk format processing on background executor with WAITING_RETRY on partial failure"
```

---

## Task 4: Block deck import during `WAITING_RETRY`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`

**Interfaces:**
- Consumes: `BulkFormatRepository.findBulkFormatByAnalysisId(UUID)` → `Optional<BulkFormatEntity>`; `BulkFormatStatus` (Task 1)
- Produces: `DeckService.importDeck(UUID, Map<String, NoteTypeTemplate>)` rejects when an active (`IN_PROGRESS`/`PENDING`/`WAITING_RETRY`) bulk format job exists

Compile-verified change (no existing `DeckService` tests; matches the prior plan's convention for this guard).

- [ ] **Step 1: Add imports and the repository dependency**

Add imports:

```java
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;
```

Add the field after `deckRepository`:

```java
  private final BulkFormatRepository bulkFormatRepository;
```

- [ ] **Step 2: Add the guard at the top of `importDeck`**

Insert as the first statement of `importDeck`, before `var analysis = analysisService.getAnalysis(analysisId);`:

```java
    var activeJob = bulkFormatRepository.findBulkFormatByAnalysisId(analysisId);
    if (activeJob.isPresent()
        && (activeJob.get().getStatus() == BulkFormatStatus.IN_PROGRESS
            || activeJob.get().getStatus() == BulkFormatStatus.PENDING
            || activeJob.get().getStatus() == BulkFormatStatus.WAITING_RETRY)) {
      throw new SmortException(
          "Cannot import while bulk format is in progress. analysisId={}", analysisId);
    }
```

- [ ] **Step 3: Verify compile**

Run: `./mvnw compile`
Expected: clean compile in a JDK-25 environment (fails locally on this JDK-21 checkout — pre-existing).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: block deck import while bulk format is active including WAITING_RETRY"
```

---

## Execution Order

```
Task 1 (data model) ──→ Task 2 (repo + cron)
                    ├──→ Task 3 (service + executor)
                    └──→ Task 4 (deck guard)
```

Task 1 must land first (enum + fields). Tasks 2–4 each depend only on Task 1 and can be executed in any order. Each task compiles and commits independently.

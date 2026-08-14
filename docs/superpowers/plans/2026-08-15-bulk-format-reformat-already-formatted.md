# Bulk Format: Reformat Already Formatted Notes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `reformatAlreadyFormatted` option to bulk format so already-formatted notes whose derived note predates the job are reformatted, and compute `totalNotes` once at job start.

**Architecture:** The bulk-format service computes the set of notes to process (via a shared filter) and `totalNotes` once in `startBulkFormat`, then hands the precomputed sets to the processing task via overloaded `dispatch`/`processNotes`. The resume (cron) path recomputes the sets itself and delegates to the same overload. The flag is persisted on the `BulkFormatEntity` so resumed jobs keep the same behavior.

**Tech Stack:** Java 17, Spring Boot, Lombok, MapStruct, DynamoDB Enhanced Client (Spring Data), SQLite (Hibernate) for Anki notes.

## Global Constraints

- **Compilation is owned by the human.** Per AGENTS.md, implementing/reviewing subagents must NOT attempt to run, fix, or debug the build (`./mvnw compile`, `./mvnw test`, etc.). Skip build/compile verification steps and note in reports that compilation was skipped. Do not add test code (no unit tests exist for `BulkFormatService`).
- **Work on the feature branch** `feat/bulk-format-reformat-already-formatted` (already created). Commit all work there; never touch `main`.
- **Follow existing code style:** 2-space indent, no comments, Lombok `@RequiredArgsConstructor` for DI, varargs-SLF4J-style messages in `SmortException`/`NotFoundException` (e.g. `"analysisId={}"`).
- **No DB migration:** DynamoDB data will be cleared by the user; stale rows do not need handling.

---

### Task 1: Add `reformatAlreadyFormatted` to `BulkFormatEntity`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatEntity.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `BulkFormatEntity(UUID analysisId, boolean reformatAlreadyFormatted)` constructor and `isReformatAlreadyFormatted()` getter (Lombok), used by Task 2.

- [ ] **Step 1: Add the field and update the constructor**

In `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatEntity.java`, add a `boolean reformatAlreadyFormatted;` field alongside the other non-key fields (after `attempts`), and change the constructor:

```java
  private int attempts;
  private boolean reformatAlreadyFormatted;

  public BulkFormatEntity(UUID analysisId, boolean reformatAlreadyFormatted) {
    this.pk = AnalysisKeys.analysisPk(analysisId);
    this.sk = BulkFormatKeys.bulkFormatSk();
    this.status = BulkFormatStatus.PENDING;
    this.createdAt = Instant.now();
    this.lastUpdatedAt = Instant.now();
    this.attempts = 0;
    this.reformatAlreadyFormatted = reformatAlreadyFormatted;
    updateGsiKeys();
  }
```

Do not add any comments. Keep `totalNotes` unchanged.

- [ ] **Step 2: Verify no other call sites break**

Search the repo for `new BulkFormatEntity(` — the only call site is `BulkFormatService.startBulkFormat` (updated in Task 2). No other files construct this entity. There is no compile step here (human owns compilation).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatEntity.java
git commit -m "feat: persist reformatAlreadyFormatted on BulkFormatEntity"
```

---

### Task 2: Rework `BulkFormatService` (start-time filter, guard, overloads)

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java`

**Interfaces:**
- Consumes:
  - `BulkFormatEntity(UUID analysisId, boolean reformatAlreadyFormatted)` and `BulkFormatEntity.isReformatAlreadyFormatted()` (Task 1).
  - `AnalysisService.getAnalysis(UUID)` → `Analysis` with `getDeckId()`, `getFormatInstructions()`.
  - `AnkiNoteRepository.findNotesByAnalysisIdAndDeckId(UUID, Long)` → `List<AnkiNoteEntity>`.
  - `DerivedNoteRepository.findDerivedNotesByAnalysisId(UUID)` → `List<DerivedNoteEntity>` with `getNoteId()`, `getLastFormattedAt()`.
  - `AnkiNoteTypeService.getNoteTypesByAnalysisId(UUID)` → `Map<Long, AnkiNoteTypeEntity>`.
  - `SmortException(HttpStatus, LogSeverity, String, Object...)` and `LogSeverity.INFO`.
- Produces:
  - `startBulkFormat(UUID analysisId, boolean reformatAlreadyFormatted)` — public, called by Task 3.
  - `getNotesToProcess(List<AnkiNoteEntity>, Map<Long, DerivedNoteEntity>, BulkFormatEntity)` — shared filter helper.

- [ ] **Step 1: Update imports and `startBulkFormat` signature**

In `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java`:

Add imports:

```java
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
```

`Collectors` is already imported. `Optional`, `Instant`, `IntStream` already imported.

Change the method signature and body of `startBulkFormat` (replace the `var job = new BulkFormatEntity(analysisId);` block and everything after it up to the end of the method):

```java
  public void startBulkFormat(UUID analysisId, boolean reformatAlreadyFormatted) {
    var existing = bulkFormatRepository.findBulkFormatByAnalysisId(analysisId);
    if (existing.isPresent()) {
      var job = existing.get();
      if (job.getStatus() == BulkFormatStatus.PENDING
          || job.getStatus() == BulkFormatStatus.IN_PROGRESS
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
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    var existingDerivedNotes =
        derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId).stream()
            .collect(Collectors.toMap(DerivedNoteEntity::getNoteId, Function.identity()));

    var job = new BulkFormatEntity(analysisId, reformatAlreadyFormatted);
    var notesToProcess = getNotesToProcess(notes, existingDerivedNotes, job);

    if (notesToProcess.isEmpty()) {
      throw new SmortException(
          HttpStatus.BAD_REQUEST,
          LogSeverity.INFO,
          "No notes to format for analysis. analysisId={}",
          analysisId);
    }

    job.setTotalNotes(notesToProcess.size());
    bulkFormatRepository.save(job);
    dispatch(job, notesToProcess, existingDerivedNotes);
  }
```

- [ ] **Step 2: Add the overloaded `dispatch`**

Add this overload immediately after the existing one-arg `dispatch`:

```java
  private void dispatch(
      BulkFormatEntity job,
      List<AnkiNoteEntity> notesToProcess,
      Map<Long, DerivedNoteEntity> existingDerivedNotes) {
    bulkFormatTaskExecutor.execute(
        () -> {
          try {
            processNotes(job, notesToProcess, existingDerivedNotes);
          } catch (Exception e) {
            log.error(
                "Unexpected error during bulk format processing. analysisId={}",
                job.getAnalysisId(),
                e);
          }
        });
  }
```

- [ ] **Step 3: Split `processNotes` into a computing overload and a processing overload**

Rename the existing `private void processNotes(BulkFormatEntity job)` body into a computing wrapper and a processing overload, as follows.

Replace the existing method header line `private void processNotes(BulkFormatEntity job) {` and the first block (analysis fetch + `existingDerivedNotes` + `notesToProcess` computation) with:

```java
  private void processNotes(BulkFormatEntity job) {
    var analysisId = job.getAnalysisId();
    Analysis analysis;
    try {
      analysis = analysisService.getAnalysis(analysisId);
    } catch (NotFoundException e) {
      throw e.withSeverity(LogSeverity.ERROR);
    }
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    var existingDerivedNotes =
        derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId).stream()
            .collect(Collectors.toMap(DerivedNoteEntity::getNoteId, Function.identity()));
    var notesToProcess = getNotesToProcess(notes, existingDerivedNotes, job);
    processNotes(job, notesToProcess, existingDerivedNotes);
  }

  private void processNotes(
      BulkFormatEntity job,
      List<AnkiNoteEntity> notesToProcess,
      Map<Long, DerivedNoteEntity> existingDerivedNotes) {
    var analysisId = job.getAnalysisId();
    Analysis analysis;
    try {
      analysis = analysisService.getAnalysis(analysisId);
    } catch (NotFoundException e) {
      throw e.withSeverity(LogSeverity.ERROR);
    }
    var noteTypes = noteTypeService.getNoteTypesByAnalysisId(analysisId);
```

Keep the rest of the original `processNotes` body (attempts/status, the `for` loop, completion/failure handling) unchanged, EXCEPT:

- Remove the line `job.setCompletedNotes(existingDerivedNotes.size());`
- Remove the line `job.setTotalNotes(notes.size());`
- Keep `job.setCompletedNotes(job.getCompletedNotes() + 1);` inside the loop as-is.

The `existingDerivedNotes` parameter is intentionally unused by the processing overload's loop; it exists so the first attempt and the resume path share one method shape.

- [ ] **Step 4: Add the shared filter helper**

Add this method to the class (e.g. after the `processNotes` overloads):

```java
  private List<AnkiNoteEntity> getNotesToProcess(
      List<AnkiNoteEntity> notes,
      Map<Long, DerivedNoteEntity> existingDerivedNotes,
      BulkFormatEntity job) {
    return notes.stream()
        .filter(
            note -> {
              var derivedNote = existingDerivedNotes.get(note.getId());
              if (derivedNote == null) {
                return true;
              }
              if (!job.isReformatAlreadyFormatted()) {
                return false;
              }
              return derivedNote
                  .getLastFormattedAt()
                  .map(lastFormattedAt -> lastFormattedAt.isBefore(job.getCreatedAt()))
                  .orElse(true);
            })
        .toList();
  }
```

- [ ] **Step 5: Verify logic consistency**

Re-read the final `BulkFormatService.java` and confirm:
- `startBulkFormat` throws INFO-severity `SmortException` with HTTP 400 when `notesToProcess` is empty, before any job is saved.
- `totalNotes` is set exactly once (in `startBulkFormat`); `processNotes` overloads never set it.
- `setCompletedNotes(...)` is only invoked as `job.setCompletedNotes(job.getCompletedNotes() + 1)` inside the success branch of the loop.
- Resume path (`resumeBulkFormat` → `dispatch(job)` → `processNotes(job)`) recomputes the filter with `job.getCreatedAt()` and delegates to the three-arg overload.
- No test code added; compilation skipped (human verifies).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java
git commit -m "feat: support reformatAlreadyFormatted in bulk format service"
```

---

### Task 3: Expose `reformatAlreadyFormatted` query parameter

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java:208-212`

**Interfaces:**
- Consumes: `BulkFormatService.startBulkFormat(UUID, boolean)` (Task 2).
- Produces: `POST /analysis/{analysisId}/format?reformatAlreadyFormatted=true` (default `true`).

- [ ] **Step 1: Add the query parameter**

Replace the `startBulkFormat` endpoint method with:

```java
  @PostMapping("/{analysisId}/format")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void startBulkFormat(
      @PathVariable UUID analysisId,
      @RequestParam(defaultValue = "true") boolean reformatAlreadyFormatted) {
    bulkFormatService.startBulkFormat(analysisId, reformatAlreadyFormatted);
  }
```

`@RequestParam` is already covered by the existing `org.springframework.web.bind.annotation.*` wildcard import.

- [ ] **Step 2: Verify no other `startBulkFormat` callers exist**

Search the repo for `startBulkFormat(` — only the controller endpoint (this task) and the service (Task 2) reference it. No other call sites to update.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java
git commit -m "feat: accept reformatAlreadyFormatted query param on bulk format endpoint"
```

---

## Completion Criteria

- `BulkFormatEntity` has `reformatAlreadyFormatted` and a two-arg constructor; `totalNotes` is still stored.
- `startBulkFormat(UUID, boolean)` computes `notesToProcess` and `totalNotes` once, throws INFO/BAD_REQUEST `SmortException` when there are no notes, and dispatches the precomputed sets.
- Resume path recomputes the sets and delegates to the same processing overload.
- `completedNotes` is only incremented per successfully formatted note.
- Endpoint accepts `reformatAlreadyFormatted` with default `true`.
- Spec document `docs/superpowers/specs/2026-08-15-bulk-format-reformat-already-formatted-design.md` and this plan are removed from the repo once the work is complete and merged (per AGENTS.md).

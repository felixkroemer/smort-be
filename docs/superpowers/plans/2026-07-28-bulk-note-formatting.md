# Bulk Note Formatting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add async bulk note formatting to analysis sessions, with cron-based auto-resume on crash, a status polling endpoint, and import blocking during active jobs.

**Architecture:** A new `BulkFormatJobEntity` in DynamoDB (single-table design, same `ANALYSIS#` partition) tracks the job lifecycle. A `BulkFormatService` iterates over all notes in an analysis, calling `ChatService.formatNote()` on raw note fields, creating `DerivedNoteEntity` records via conditional writes (skip if already exists). A `BulkFormatCron` runs every 5 minutes, detecting crashed jobs (`IN_PROGRESS` with `lastUpdatedAt` older than 5 minutes) and re-triggering them. Import is blocked while a job is active.

**Tech Stack:** Java 25, Spring Boot 4.0.3, DynamoDB Enhanced SDK, OpenAI Java SDK, Postgres/JPA, Liquibase

## Global Constraints

- DynamoDB table: `common-table` (single-table design)
- All DynamoDB entities use `@DynamoDbBean` + Lombok `@Getter/@Setter/@NoArgsConstructor`
- Partition key format for analysis items: `ANALYSIS#<UUID>`
- Sort key constants live in `keys/sort/` utility classes
- Spring scheduling: `@Scheduled(cron = "...")` pattern (see `DeckCron` for precedent)
- No `@EnableScheduling` currently present — must be added
- No existing test infrastructure beyond `SmortApplicationTests` context-loads test

---

## File Structure

**New files:**
| File | Responsibility |
|---|---|
| `infrastructure/dynamodb/anki/BulkFormatJobEntity.java` | DynamoDB entity for bulk format job lifecycle |
| `infrastructure/dynamodb/anki/BulkFormatJobRepository.java` | CRUD for `BulkFormatJobEntity` |
| `domain/anki/BulkFormatService.java` | Core bulk format logic: iterate notes, call AI, save |
| `domain/cron/BulkFormatCron.java` | Scheduled job: detect crashed IN_PROGRESS jobs, re-trigger |
| `application/anki/dto/BulkFormatStatusResponse.java` | Response DTO: status, createdAt, lastUpdatedAt |

**Modify existing files:**
| File | Change |
|---|---|
| `infrastructure/dynamodb/anki/BulkFormatEntity.java` | Delete — replaced by `BulkFormatJobEntity` |
| `infrastructure/dynamodb/anki/BulkFormatRepository.java` | Delete — replaced by `BulkFormatJobRepository` |
| `infrastructure/dynamodb/anki/BulkFormatStatus.java` | Keep — reuse existing enum |
| `infrastructure/dynamodb/DynamoDbClientConfig.java` | Add `DynamoDbTable<BulkFormatJobEntity>` bean |
| `application/anki/AnalysisController.java` | Add `POST /analysis/{id}/format` and `GET /analysis/{id}/format/status` endpoints |
| `domain/anki/AnkiNoteService.java` | Remove empty `bulkFormatNotes()` stub |
| `domain/deck/DeckService.java` | Add import guard: check for active bulk format before importing |
| `SmortApplication.java` | Add `@EnableScheduling` |
| `application/cron/CronController.java` | Add manual trigger endpoint for bulk format cron |

---

### Task 1: Replace BulkFormatEntity stubs with BulkFormatJobEntity

The existing `BulkFormatEntity` has no timestamps. Replace it entirely.

**Files:**
- Delete: `infrastructure/dynamodb/anki/BulkFormatEntity.java`
- Delete: `infrastructure/dynamodb/anki/BulkFormatRepository.java`
- Create: `infrastructure/dynamodb/anki/BulkFormatJobEntity.java`
- Create: `infrastructure/dynamodb/anki/BulkFormatJobRepository.java`
- Keep: `infrastructure/dynamodb/anki/BulkFormatStatus.java` (unchanged)
- Modify: `infrastructure/dynamodb/DynamoDbClientConfig.java` — add table bean

- [ ] **Step 1: Create `BulkFormatJobEntity`**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class BulkFormatJobEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private BulkFormatStatus status;
  private Instant createdAt;
  private Instant lastUpdatedAt;

  public BulkFormatJobEntity(UUID analysisId) {
    this.pk = AnalysisKeys.analysisPk(analysisId);
    this.sk = BulkFormatKeys.bulkFormatSk();
    this.status = BulkFormatStatus.PENDING;
    this.createdAt = Instant.now();
    this.lastUpdatedAt = Instant.now();
  }

  public UUID getAnalysisId() {
    return UUID.fromString(pk.substring("ANALYSIS#".length()));
  }
}
```

- [ ] **Step 2: Create `BulkFormatJobRepository`**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.ExpressionFilterBuilder;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BulkFormatJobRepository {

  private final DynamoDbTable<BulkFormatJobEntity> bulkFormatJobTable;

  public Optional<BulkFormatJobEntity> findByAnalysisId(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(BulkFormatKeys.bulkFormatSk())
            .build();
    return Optional.ofNullable(bulkFormatJobTable.getItem(key));
  }

  public List<BulkFormatJobEntity> findAllInProgress() {
    return bulkFormatJobTable
        .scan(
            ScanEnhancedRequest.builder()
                .filterExpression(
                    ExpressionFilterBuilder.expressionAttribute("status")
                        .eq(BulkFormatStatus.IN_PROGRESS))
                .build())
        .items()
        .stream()
        .toList();
  }

  public void save(BulkFormatJobEntity entity) {
    bulkFormatJobTable.putItem(entity);
  }
}
```

- [ ] **Step 3: Register `DynamoDbTable<BulkFormatJobEntity>` bean in `DynamoDbClientConfig`**

Add after the `chatMessageResponseTable` bean (around line 63):

```java
@Bean
public DynamoDbTable<BulkFormatJobEntity> bulkFormatJobTable(DynamoDbEnhancedClient enhancedClient) {
  return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(BulkFormatJobEntity.class));
}
```

Add import for `BulkFormatJobEntity`.

- [ ] **Step 4: Delete old stub files**

Delete `BulkFormatEntity.java` and `BulkFormatRepository.java`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add BulkFormatJobEntity with job lifecycle tracking"
```

---

### Task 2: Add `@EnableScheduling` and configure cron

**Files:**
- Modify: `SmortApplication.java` — add annotation
- Modify: `application.properties` — add cron property
- Modify: `application-local.properties` — disable cron locally

- [ ] **Step 1: Add `@EnableScheduling` to `SmortApplication`**

```java
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class SmortApplication {

  public static void main(String[] args) {
    SpringApplication.run(SmortApplication.class, args);
  }
}
```

Add import: `import org.springframework.scheduling.annotation.EnableScheduling;`

- [ ] **Step 2: Add cron property to `application.properties`**

```properties
app.scheduling.bulk-format-cron: "*/5 * * * *"
```

- [ ] **Step 3: Disable cron locally in `application-local.properties`**

```properties
app.scheduling.bulk-format-cron: "-"
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: enable scheduling, add bulk format cron property"
```

---

### Task 3: Implement `BulkFormatService`

This is the core service. It iterates notes, calls `ChatService.formatNote()` on raw fields, and saves `DerivedNoteEntity` via conditional writes.

**Files:**
- Create: `domain/anki/BulkFormatService.java`

**Depends on:** Task 1 (entity, repository)

- [ ] **Step 1: Create `BulkFormatService`**

```java
package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkFormatService {

  private final BulkFormatJobRepository bulkFormatJobRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final AnkiNoteRepository ankiNoteRepository;
  private final AnkiNoteTypeService noteTypeService;
  private final AnalysisService analysisService;
  private final ChatService chatService;

  public void startBulkFormat(UUID analysisId) {
    var existing = bulkFormatJobRepository.findByAnalysisId(analysisId);
    if (existing.isPresent()) {
      var job = existing.get();
      if (job.getStatus() == BulkFormatStatus.IN_PROGRESS
          || job.getStatus() == BulkFormatStatus.PENDING) {
        throw new SmortException(
            "Bulk format already in progress for analysis. analysisId={}", analysisId);
      }
      if (job.getStatus() == BulkFormatStatus.COMPLETED) {
        throw new SmortException(
            "Bulk format already completed for analysis. analysisId={}", analysisId);
      }
    }

    var job = new BulkFormatJobEntity(analysisId);
    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    bulkFormatJobRepository.save(job);

    processNotes(analysisId, job);
  }

  public void resumeBulkFormat(UUID analysisId) {
    var job =
        bulkFormatJobRepository
            .findByAnalysisId(analysisId)
            .orElseThrow(
                () -> new SmortException("No bulk format job found. analysisId={}", analysisId));

    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setLastUpdatedAt(Instant.now());
    bulkFormatJobRepository.save(job);

    processNotes(analysisId, job);
  }

  private void processNotes(UUID analysisId, BulkFormatJobEntity job) {
    var analysis = analysisService.getAnalysis(analysisId);
    var notes =
        ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    var noteTypes = noteTypeService.getNoteTypesByAnalysisId(analysisId);

    int processed = 0;
    int failed = 0;

    for (var noteEntity : notes) {
      var existing =
          derivedNoteRepository.finDerivedNotedByAnalysisIdAndNoteId(
              analysisId, noteEntity.getId());
      if (existing.isPresent()) {
        processed++;
        continue;
      }

      try {
        var noteType = noteTypes.get(noteEntity.getNoteTypeId());
        var typeFieldNames = noteType.getFields();
        var content =
            IntStream.range(0, typeFieldNames.size())
                .boxed()
                .collect(Collectors.toMap(typeFieldNames::get, noteEntity.getFlds()::get));

        var noteSchema = chatService.formatNote(content);
        var derivedNote =
            new DerivedNoteEntity(
                analysisId, noteEntity.getId(), noteSchema.getFront(), noteSchema.getBack());
        derivedNoteRepository.save(derivedNote);
        processed++;

        job.setLastUpdatedAt(Instant.now());
        bulkFormatJobRepository.save(job);

      } catch (Exception e) {
        failed++;
        log.warn(
            "Failed to format note during bulk format. analysisId={}, noteId={}",
            analysisId,
            noteEntity.getId(),
            e);
      }
    }

    job.setStatus(BulkFormatStatus.COMPLETED);
    job.setLastUpdatedAt(Instant.now());
    bulkFormatJobRepository.save(job);

    log.info(
        "Bulk format complete. analysisId={}, processed={}, failed={}",
        analysisId,
        processed,
        failed);
  }

  public BulkFormatJobEntity getJobStatus(UUID analysisId) {
    return bulkFormatJobRepository
        .findByAnalysisId(analysisId)
        .orElseThrow(
            () -> new SmortException("No bulk format job found. analysisId={}", analysisId));
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java
git commit -m "feat: implement BulkFormatService with auto-resume"
```

---

### Task 4: Add controller endpoints

**Files:**
- Create: `application/anki/dto/BulkFormatStatusResponse.java`
- Modify: `application/anki/AnalysisController.java` — add two endpoints

**Depends on:** Task 3

- [ ] **Step 1: Create response DTO**

```java
package com.felixkroemer.smort.application.anki.dto;

import java.time.Instant;

public record BulkFormatStatusResponse(String status, Instant createdAt, Instant lastUpdatedAt) {}
```

- [ ] **Step 2: Add endpoints and injection to `AnalysisController`**

Add `BulkFormatService` as a constructor parameter (the class uses `@RequiredArgsConstructor`).

Add endpoints:

```java
@PostMapping("/{analysisId}/format")
@ResponseStatus(HttpStatus.ACCEPTED)
public void startBulkFormat(@PathVariable UUID analysisId) {
  bulkFormatService.startBulkFormat(analysisId);
}

@GetMapping("/{analysisId}/format/status")
public BulkFormatStatusResponse getBulkFormatStatus(@PathVariable UUID analysisId) {
  var job = bulkFormatService.getJobStatus(analysisId);
  return new BulkFormatStatusResponse(
      job.getStatus().name(), job.getCreatedAt(), job.getLastUpdatedAt());
}
```

Add import for `BulkFormatService` and `HttpStatus`.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add bulk format controller endpoints"
```

---

### Task 5: Create `BulkFormatCron` for auto-resume

**Files:**
- Create: `domain/cron/BulkFormatCron.java`
- Modify: `application/cron/CronController.java` — add manual trigger

**Depends on:** Task 2 (scheduling enabled), Task 3 (service)

- [ ] **Step 1: Create `BulkFormatCron`**

```java
package com.felixkroemer.smort.domain.cron;

import com.felixkroemer.smort.domain.anki.BulkFormatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatJobRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkFormatCron {

  private static final Duration CRASH_TIMEOUT = Duration.ofMinutes(5);

  private final BulkFormatJobRepository bulkFormatJobRepository;
  private final BulkFormatService bulkFormatService;

  @Scheduled(cron = "${app.scheduling.bulk-format-cron}")
  public void resumeCrashedBulkFormats() {
    var allJobs = bulkFormatJobRepository.findAllInProgress();
    for (var job : allJobs) {
      if (Duration.between(job.getLastUpdatedAt(), Instant.now()).compareTo(CRASH_TIMEOUT) > 0) {
        log.warn(
            "Resuming crashed bulk format. analysisId={}, lastUpdate={}",
            job.getAnalysisId(),
            job.getLastUpdatedAt());
        try {
          bulkFormatService.resumeBulkFormat(job.getAnalysisId());
        } catch (Exception e) {
          log.error(
              "Failed to resume bulk format. analysisId={}", job.getAnalysisId(), e);
        }
      }
    }
  }
}
```

- [ ] **Step 2: Add manual trigger to `CronController`**

Add `BulkFormatCron` as a constructor parameter. Add endpoint:

```java
@PostMapping("/resumeCrashedBulkFormats")
public void resumeCrashedBulkFormats() {
  bulkFormatCron.resumeCrashedBulkFormats();
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add BulkFormatCron for auto-resume of crashed jobs"
```

---

### Task 6: Block import while bulk format is active

**Files:**
- Modify: `domain/deck/DeckService.java` — add guard in `importDeck()`

**Depends on:** Task 1 (repository)

- [ ] **Step 1: Add import guard to `DeckService.importDeck()`**

Inject `BulkFormatJobRepository` into `DeckService`.

At the top of `importDeck()`, before any processing (after line 37):

```java
var activeJob = bulkFormatJobRepository.findByAnalysisId(analysisId);
if (activeJob.isPresent()
    && (activeJob.get().getStatus() == BulkFormatStatus.IN_PROGRESS
        || activeJob.get().getStatus() == BulkFormatStatus.PENDING)) {
  throw new SmortException(
      "Cannot import while bulk format is in progress. analysisId={}", analysisId);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: block deck import while bulk format is in progress"
```

---

### Task 7: Clean up stubs

**Files:**
- Modify: `domain/anki/AnkiNoteService.java` — remove empty `bulkFormatNotes()` stub

**Depends on:** Task 3 (service replaces stub)

- [ ] **Step 1: Remove `bulkFormatNotes()` from `AnkiNoteService`**

Delete lines 79-84 (the empty stub method).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java
git commit -m "chore: remove empty bulkFormatNotes stub"
```

---

## Execution Order

Tasks 1 and 2 are independent and can run in parallel. Task 3 depends on 1. Task 4 depends on 3. Task 5 depends on 2 and 3. Task 6 depends on 1. Task 7 depends on 3.

```
Task 1 ─────┐
             ├──→ Task 3 ──→ Task 4
Task 2 ─────┤             ──→ Task 7
             │
             └──→ Task 5
Task 6 ────────────────────── (after Task 1)
```

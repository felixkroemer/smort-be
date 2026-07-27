# Bulk Note Formatting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add async bulk note formatting to analysis sessions, with cron-based auto-resume on crash, a status polling endpoint, and import blocking during active jobs.

**Architecture:** `BulkFormatEntity` in DynamoDB (single-table design, same `ANALYSIS#` partition) tracks the job lifecycle with `StatusBulkFormatIndex` GSI for efficient status queries. `BulkFormatService` iterates over all notes in an analysis, calling `ChatService.formatNote()` on raw note fields, creating `DerivedNoteEntity` records via conditional writes (skip if already exists). `BulkFormatCron` runs every 5 minutes, detecting crashed jobs (`IN_PROGRESS` with `lastUpdatedAt` older than 5 minutes) and re-triggering them. Import is blocked while a job is active.

**Tech Stack:** Java 25, Spring Boot 4.0.3, DynamoDB Enhanced SDK, OpenAI Java SDK, Postgres/JPA, Liquibase

## Global Constraints

- DynamoDB table: `common-table` (single-table design)
- All DynamoDB entities use `@DynamoDbBean` + Lombok `@Getter/@Setter/@NoArgsConstructor`
- Partition key format for analysis items: `ANALYSIS#<UUID>`
- Sort key constants live in `keys/sort/` utility classes
- Spring scheduling: `@Scheduled(cron = "...")` pattern (see `DeckCron` for precedent)
- No existing test infrastructure beyond `SmortApplicationTests` context-loads test

---

## File Structure

**Completed:**
| File | Change |
|---|---|
| `infrastructure/dynamodb/anki/BulkFormatEntity.java` | Added `StatusBulkFormatIndex` GSI fields and `updateGsiKeys()` |
| `infrastructure/dynamodb/anki/BulkFormatRepository.java` | Added `findAllInProgress()` query using GSI |
| `domain/anki/BulkFormatService.java` | Core bulk format logic (already existed) |
| `domain/cron/BulkFormatCron.java` | Created — scheduled job for auto-resume |
| `application/cron/CronController.java` | Added manual trigger endpoint |
| `SmortApplication.java` | Added `@EnableScheduling` |
| `application.properties` | Added `app.scheduling.bulk-format-cron` |
| `application-local.properties` | Disabled cron locally |
| `application/anki/dto/BulkFormatStatusResponse.java` | Response DTO (already existed) |
| `application/anki/AnalysisController.java` | Added `startBulkFormat` and `getBulkFormatStatus` endpoints |

**Pending changes:**
| File | Change |
|---|---|
| `domain/deck/DeckService.java` | Add import guard: check for active bulk format before importing |

---

## Completed Tasks

### Task 1: Add StatusBulkFormatIndex GSI to BulkFormatEntity ✅

**Status:** Done

### Task 2: Add @EnableScheduling and configure cron ✅

**Status:** Done

### Task 3: BulkFormatService ✅

**Status:** Already existed with working implementation

### Task 4: Controller endpoints ✅

**Status:** Already existed — `POST /analysis/{id}/format` and `GET /analysis/{id}/format/status`

### Task 5: BulkFormatCron ✅

**Status:** Done

---

## Pending Tasks

### Task 6: Block import while bulk format is active

**Files:**
- Modify: `domain/deck/DeckService.java` — add guard in `importDeck()`

**Depends on:** Task 1 (repository)

- [ ] **Step 1: Add import guard to `DeckService.importDeck()`**

Inject `BulkFormatRepository` into `DeckService`.

At the top of `importDeck()`, before any processing (after line 38):

```java
var activeJob = bulkFormatRepository.findBulkFormatByAnalysisId(analysisId);
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

## Execution Order

```
Task 1 ✅ ─────┐
               ├──→ Task 3 ✅ ──→ Task 4 ✅
Task 2 ✅ ─────┤                 ──→ Task 7 ✅ (no stub to remove)
               │
               └──→ Task 5 ✅
Task 6 ────────────────────── (after Task 1)
```

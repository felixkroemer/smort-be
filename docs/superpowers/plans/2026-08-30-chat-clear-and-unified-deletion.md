# Chat Clearing and Unified Async Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add DELETE endpoints to clear a deck's, a deck note's, and an anki note's chat; delete chats (and the deck bulk-format job) when a deck is deleted; unify deck and analysis deletion behind a single async `CleanupCron` (renamed from `DeckCron`); and make analysis deletion asynchronous (mark-for-deletion + cron) instead of synchronous.

**Architecture:** Chat messages live in one DynamoDB table keyed `pk` (container: `DECK#<id>` / `ANALYSIS#<id>`) + `sk` (`CHAT#C#<entityId>#...` assistant / `CHAT#U#<entityId>#...` user tool-call). We add batch-delete methods to `ChatRepository` (`deleteAll(pk)` for container cleanup, `deleteChat(pk, entityId)` for one thread) mirroring the existing `DeckRepository.deleteDeckNotes` chunked-25 batch pattern. Clear endpoints delegate to `deleteChat`. `CleanupCron` replaces `DeckCron` and hosts both scheduled cleanups inline: decks delete notes → draft note → bulk-format job → chats → meta; analyses delete db file → derived notes → bulk-format job → chats → meta. `AnalysisService.deleteAnalysis` becomes mark-only. `AnalysisMetaRepository.findAllAnalysisMetas()` gains an active-status filter (excludes `MARKED_FOR_DELETION`) and a new `scanForAnalysesMarkedForDeletion()`.

**Tech Stack:** Java 21, Spring (`@EnableScheduling`, `@Scheduled`), AWS SDK DynamoDB Enhanced Client, Lombok, MapStruct.

## Global Constraints

- **No tests.** Project convention (AGENTS.md): tests are only written when explicitly requested. This plan contains no test files.
- **No build verification.** Per AGENTS.md, implementing subagents must NOT run/fix/debug the build (`./mvnw compile`, `./mvnw test`). The human owns compilation. Do not run build commands; note "compilation skipped per AGENTS.md" in commit messages and reports.
- **Branch:** Work on `feature/chat-clear-and-unified-deletion` only. Never commit to main.
- **Batch-delete pattern:** mirror `DeckRepository.deleteDeckNotes` (query projecting `pk`/`sk`, `IntStream` chunking by 25, `WriteBatch` + `dynamoDbEnhancedClient.batchWriteItem`).
- **Cron pattern:** `@Scheduled(cron = "${app.scheduling.<key>}")`, property in `application.properties`, POST trigger in `CronController` (mirrors the existing deck cron).
- Existing code style: 2-space indent, no comments, alphabetical import grouping.

---

### Task 1: Add chat deletion methods to `ChatRepository`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/chat/ChatRepository.java`

**Interfaces:**
- Consumes: existing `ChatKeys.llmChatMessagesPrefix`, `ChatKeys.userChatMessagesPrefix`, `DynamoDbTable<ChatMessageEntity> table`. Mirrors the batch-delete pattern in `DeckRepository.deleteDeckNotes` (`infrastructure/dynamodb/deck/DeckRepository.java:119-156`).
- Produces: `void deleteAll(String pk)` and `<T> void deleteChat(String pk, T entityId)`. Consumed by Tasks 3 and 4.

- [ ] **Step 1: Add imports**

Add these imports alphabetically (keep existing ones):

```java
import java.util.stream.IntStream;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
```

- [ ] **Step 2: Add the `DynamoDbEnhancedClient` field**

After `private final DynamoDbTable<ChatMessageEntity> table;` add:

```java
  private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
```

- [ ] **Step 3: Add `deleteAll`, `deleteChat`, and the two private helpers**

Add after `saveInTx` (end of class):

```java
  public void deleteAll(String pk) {
    deleteKeys(queryKeys(pk, "CHAT#"));
  }

  public <T> void deleteChat(String pk, T entityId) {
    deleteKeys(
        Stream.concat(
                queryKeys(pk, ChatKeys.llmChatMessagesPrefix(entityId)).stream(),
                queryKeys(pk, ChatKeys.userChatMessagesPrefix(entityId)).stream())
            .toList());
  }

  private List<ChatMessageEntity> queryKeys(String pk, String sortKeyPrefix) {
    return table
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.sortBeginsWith(
                        Key.builder().partitionValue(pk).sortValue(sortKeyPrefix).build()))
                .attributesToProject("pk", "sk")
                .build())
        .items()
        .stream()
        .toList();
  }

  private void deleteKeys(List<ChatMessageEntity> keys) {
    IntStream.range(0, (keys.size() + 24) / 25)
        .mapToObj(i -> keys.subList(i * 25, Math.min((i + 1) * 25, keys.size())))
        .forEach(
            batch -> {
              WriteBatch.Builder<ChatMessageEntity> writeBatch =
                  WriteBatch.builder(ChatMessageEntity.class).mappedTableResource(table);
              batch.forEach(
                  item ->
                      writeBatch.addDeleteItem(
                          Key.builder()
                              .partitionValue(item.getPk())
                              .sortValue(item.getSk())
                              .build()));
              dynamoDbEnhancedClient.batchWriteItem(
                  BatchWriteItemEnhancedRequest.builder().writeBatches(writeBatch.build()).build());
            });
  }
```

`Stream` is already imported. `deleteKeys` with an empty list is a no-op (`IntStream.range(0, 0)`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/chat/ChatRepository.java
git commit -m "feat: add chat deletion methods to ChatRepository (compilation skipped per AGENTS.md)"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 2: Filter analyses by active status and add marked-for-deletion scan

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaRepository.java`

**Interfaces:**
- Consumes: `AnalysisStatus` (`infrastructure/dynamodb/anki/AnalysisStatus.java`, has `MARKED_FOR_DELETION`), existing scan pattern.
- Produces: `findAllAnalysisMetas()` now excludes `MARKED_FOR_DELETION`; new `List<AnalysisMetaEntity> scanForAnalysesMarkedForDeletion()`. The scan method is consumed by Task 4.

- [ ] **Step 1: Add the active-status filter to `findAllAnalysisMetas`**

Replace the `Expression filter = ...` block in `findAllAnalysisMetas()` (currently `#sk = :sk AND begins_with(#pk, :pkPrefix)`):

```java
    Expression filter =
        Expression.builder()
            .expression("#sk = :sk AND begins_with(#pk, :pkPrefix) AND #status <> :status")
            .expressionNames(Map.of("#sk", "sk", "#pk", "pk", "#status", "status"))
            .expressionValues(
                Map.of(
                    ":sk", AttributeValue.fromS(MetaKeys.metaSk()),
                    ":pkPrefix", AttributeValue.fromS(AnalysisKeys.analysisPkPrefix()),
                    ":status", AttributeValue.fromS(AnalysisStatus.MARKED_FOR_DELETION.toString())))
            .build();
```

The rest of `findAllAnalysisMetas()` is unchanged.

- [ ] **Step 2: Add `scanForAnalysesMarkedForDeletion`**

Add after `findAllAnalysisMetas()`:

```java
  public List<AnalysisMetaEntity> scanForAnalysesMarkedForDeletion() {
    Expression filter =
        Expression.builder()
            .expression("#sk = :sk AND begins_with(#pk, :pkPrefix) AND #status = :status")
            .expressionNames(Map.of("#sk", "sk", "#pk", "pk", "#status", "status"))
            .expressionValues(
                Map.of(
                    ":sk", AttributeValue.fromS(MetaKeys.metaSk()),
                    ":pkPrefix", AttributeValue.fromS(AnalysisKeys.analysisPkPrefix()),
                    ":status", AttributeValue.fromS(AnalysisStatus.MARKED_FOR_DELETION.toString())))
            .build();

    return analysisMetaTable
        .scan(ScanEnhancedRequest.builder().filterExpression(filter).build())
        .items()
        .stream()
        .toList();
  }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaRepository.java
git commit -m "feat: filter analyses by status and add marked-for-deletion scan (compilation skipped per AGENTS.md)"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 3: Add clear-chat service methods and DELETE endpoints

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java`

**Interfaces:**
- Consumes: `ChatRepository.deleteChat(String pk, Object entityId)` from Task 1.
- Produces: `DeckService.clearChat(UUID)`, `NoteService.clearChat(UUID, UUID)`, `AnkiNoteService.clearChat(UUID, Long)`, and three `DELETE` endpoints returning `204 No Content`.

- [ ] **Step 1: `DeckService` — inject `ChatRepository` and add `clearChat`**

Add the import `import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;` (alphabetical with the other `infrastructure.dynamodb.chat`/`deck` imports), the field `private final ChatRepository chatRepository;`, and the method:

```java
  public void clearChat(UUID deckId) {
    chatRepository.deleteChat(DeckKeys.deckPk(deckId), deckId);
  }
```

`DeckKeys` is already imported. Place `clearChat` near `getChat`.

- [ ] **Step 2: `NoteService` — inject `ChatRepository` and add `clearChat`**

Add the import `import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;`, the field `private final ChatRepository chatRepository;`, and the method:

```java
  public void clearChat(UUID deckId, UUID noteId) {
    chatRepository.deleteChat(DeckKeys.deckPk(deckId), noteId);
  }
```

`DeckKeys` is already imported.

- [ ] **Step 3: `AnkiNoteService` — inject `ChatRepository` and add `clearChat`**

Add the import `import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;`, the field `private final ChatRepository chatRepository;`, and the method:

```java
  public void clearChat(UUID analysisId, Long noteId) {
    chatRepository.deleteChat(AnalysisKeys.analysisPk(analysisId), noteId);
  }
```

`AnalysisKeys` is already imported.

- [ ] **Step 4: `DeckController` — add two endpoints**

Add after the existing `getChat` handler (`@GetMapping("/{deckId}/chat")`):

```java
  @DeleteMapping("/{deckId}/chat")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearChat(@PathVariable("deckId") UUID deckId) {
    deckService.clearChat(deckId);
  }

  @DeleteMapping("/{deckId}/notes/{noteId}/chat")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearNoteChat(
      @PathVariable("deckId") UUID deckId, @PathVariable("noteId") UUID noteId) {
    noteService.clearChat(deckId, noteId);
  }
```

`HttpStatus` is already imported.

- [ ] **Step 5: `AnalysisController` — add one endpoint**

Add after the existing `getChat` handler (`@GetMapping("/{analysisId}/notes/{noteId}/chat")`):

```java
  @DeleteMapping("/{analysisId}/notes/{noteId}/chat")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearChat(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    ankiNoteService.clearChat(analysisId, noteId);
  }
```

`HttpStatus` is already imported.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java src/main/java/com/felixkroemer/smort/application/deck/DeckController.java src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java
git commit -m "feat: add clear-chat endpoints for deck, note, and anki note (compilation skipped per AGENTS.md)"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 4: Unify async deletion in `CleanupCron`

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/cron/CleanupCron.java`
- Delete: `src/main/java/com/felixkroemer/smort/domain/cron/DeckCron.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/cron/CronController.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: Task 1's `ChatRepository.deleteAll(String pk)`; Task 2's `AnalysisMetaRepository.scanForAnalysesMarkedForDeletion()`; existing `DeckRepository.scanForDecksMarkedForDeletion`, `deleteDeckNotes`, `deleteDeckMeta`; `DraftNoteRepository.delete`; `BulkFormatRepository.deleteDeckJob(UUID)` and `BulkFormatRepository.delete(UUID)`; `DerivedNoteRepository.deleteAnalysisDerivedNotes(UUID)`; `AnalysisMetaRepository.delete(UUID)`.
- Produces: `CleanupCron.deleteDecksMarkedForDeletion()` and `CleanupCron.deleteAnalysesMarkedForDeletion()` (both `@Scheduled`); `AnalysisService.deleteAnalysis(UUID)` mark-only; `CronController` triggers; new property `app.scheduling.delete-marked-analyses-cron`.

- [ ] **Step 1: Make `AnalysisService.deleteAnalysis` mark-only**

Replace the whole `deleteAnalysis` method (currently lines 195-210, including the synchronous `try` block) with:

```java
  public void deleteAnalysis(UUID analysisId) {
    var analysis = getMeta(analysisId);
    analysis.setStatus(AnalysisStatus.MARKED_FOR_DELETION);
    analysisMetaRepository.save(analysis);
  }
```

The `Files`/`Path`/`StandardOpenOption` imports remain (still used by `uploadDB`). No other changes to `AnalysisService`.

- [ ] **Step 2: Create `CleanupCron`**

Create `src/main/java/com/felixkroemer/smort/domain/cron/CleanupCron.java` with exactly:

```java
package com.felixkroemer.smort.domain.cron;

import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisMetaRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupCron {

  private final DeckRepository deckRepository;
  private final DraftNoteRepository draftNoteRepository;
  private final BulkFormatRepository bulkFormatRepository;
  private final ChatRepository chatRepository;
  private final AnalysisMetaRepository analysisMetaRepository;
  private final DerivedNoteRepository derivedNoteRepository;

  @Scheduled(cron = "${app.scheduling.delete-marked-decks-cron}")
  public void deleteDecksMarkedForDeletion() {
    for (var deck : deckRepository.scanForDecksMarkedForDeletion()) {
      try {
        deckRepository.deleteDeckNotes(deck.getDeckId());
        draftNoteRepository.delete(deck.getDeckId());
        bulkFormatRepository.deleteDeckJob(deck.getDeckId());
        chatRepository.deleteAll(DeckKeys.deckPk(deck.getDeckId()));
        deckRepository.deleteDeckMeta(deck.getDeckId());
      } catch (Exception e) {
        log.error("Could not fully delete deck marked for deletion. deckId={}", deck.getDeckId());
      }
    }
  }

  @Scheduled(cron = "${app.scheduling.delete-marked-analyses-cron}")
  public void deleteAnalysesMarkedForDeletion() {
    for (var analysis : analysisMetaRepository.scanForAnalysesMarkedForDeletion()) {
      try {
        if (analysis.getDbPath() != null) {
          Files.deleteIfExists(Path.of(analysis.getDbPath()));
        }
        derivedNoteRepository.deleteAnalysisDerivedNotes(analysis.getAnalysisId());
        bulkFormatRepository.delete(analysis.getAnalysisId());
        chatRepository.deleteAll(AnalysisKeys.analysisPk(analysis.getAnalysisId()));
        analysisMetaRepository.delete(analysis.getAnalysisId());
      } catch (Exception e) {
        log.warn("Could not fully delete analysis. analysisId={}", analysis.getAnalysisId(), e);
      }
    }
  }
}
```

- [ ] **Step 3: Delete `DeckCron`**

```bash
git rm src/main/java/com/felixkroemer/smort/domain/cron/DeckCron.java
```

- [ ] **Step 4: Update `CronController`**

Replace the import `import com.felixkroemer.smort.domain.cron.DeckCron;` with `import com.felixkroemer.smort.domain.cron.CleanupCron;`, rename the field `private final DeckCron deckCron;` to `private final CleanupCron cleanupCron;`, update the body of `deleteDecksMarkedForDeletion()` to call `cleanupCron.deleteDecksMarkedForDeletion();`, and add a new trigger:

```java
  @PostMapping("/deleteAnalysesMarkedForDeletion")
  public void deleteAnalysesMarkedForDeletion() {
    cleanupCron.deleteAnalysesMarkedForDeletion();
  }
```

- [ ] **Step 5: Add the cron property**

Append to `src/main/resources/application.properties`:

```properties
app.scheduling.delete-marked-analyses-cron:0 * * * * *
```

- [ ] **Step 6: Verify no stale `DeckCron` references**

Grep the repo for `DeckCron`. Only the deleted file path may match in git history; no `.java` source may reference it.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/cron/CleanupCron.java src/main/java/com/felixkroemer/smort/domain/cron/DeckCron.java src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java src/main/java/com/felixkroemer/smort/application/cron/CronController.java src/main/resources/application.properties
git commit -m "feat: unify async deletion in CleanupCron (compilation skipped per AGENTS.md)"
```

Note in the report: compilation skipped per AGENTS.md.
# Deck Bulk Format Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend bulk formatting from analyses-only to imported decks, using two domain services sharing a generic engine.

**Architecture:** Extract the async loop/retry/status machinery into `BulkFormatEngine` (domain/common). Make `BulkFormatEntity` abstract with `AnalysisBulkFormatEntity` (pk `ANALYSIS#`) and `DeckBulkFormatEntity` (pk `DECK#`), each with its own `sk` so the resume cron can route. Refactor the existing analysis `BulkFormatService` to delegate to the engine; add `DeckBulkFormatService`, `DeckController` endpoints, `NoteEntity.lastFormattedAt`, and cron routing.

**Tech Stack:** Java 17+, Spring Boot, Maven (`./mvnw`), AWS DynamoDB Enhanced Client (single `common-table`), MapStruct, Lombok, OpenAI Responses API via `ChatService`.

## Global Constraints

- **Do NOT run the build.** Compilation and tests are the human's job and are verified later. Never run `./mvnw compile`, `./mvnw test`, etc. Skip build/compile verification steps and note in reports that compilation was skipped per this instruction.
- **Do NOT write tests.** Tests are only added when explicitly requested; none are requested here.
- Commit all work to the current feature branch `feat/deck-bulk-format`; leave `main` untouched.
- Follow existing code style: Lombok (`@RequiredArgsConstructor`, `@Getter`/`@Setter`, `@Slf4j`), `var`, records, 2-space indent.
- `BulkFormatResponse` and `BulkFormatRestMapper` stay in `application/anki`; the deck controller reuses them.

---

### Task 1: Move `BulkFormatStatus` to the shared `infrastructure.dynamodb` package

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/BulkFormatStatus.java`
- Delete: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatStatus.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormat.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/mapping/BulkFormatRestMapper.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatEntity.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepository.java`

**Interfaces:**
- Produces: `com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus` enum (values unchanged: `PENDING`, `IN_PROGRESS`, `WAITING_RETRY`, `COMPLETED`, `FAILED`, `CANCELLED`). All later tasks import it from the new package.

- [ ] **Step 1: Create the enum in the shared package**

Create `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/BulkFormatStatus.java`:

```java
package com.felixkroemer.smort.infrastructure.dynamodb;

public enum BulkFormatStatus {
  PENDING,
  IN_PROGRESS,
  WAITING_RETRY,
  COMPLETED,
  FAILED,
  CANCELLED
}
```

(Use the exact same enum values as the existing `infrastructure/dynamodb/anki/BulkFormatStatus.java`, then delete that file.)

- [ ] **Step 2: Fix the explicit imports**

- `domain/anki/BulkFormat.java`: change `import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;` → `import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;`
- `application/anki/mapping/BulkFormatRestMapper.java`: same import change.
- `domain/deck/DeckService.java`: same import change.
- `infrastructure/dynamodb/anki/BulkFormatEntity.java`: add `import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;` (it was previously in the same package).
- `infrastructure/dynamodb/anki/BulkFormatRepository.java`: add the same import.
- `domain/anki/BulkFormatService.java`: it uses `import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;` (wildcard), so add `import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;`.

- [ ] **Step 3: Commit**

```bash
git add -A src/main/java/com/felixkroemer/smort/infrastructure/dynamodb
git add src/main/java/com/felixkroemer/smort/domain/anki/BulkFormat.java
git add src/main/java/com/felixkroemer/smort/application/anki/mapping/BulkFormatRestMapper.java
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git add src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java
git commit -m "refactor: move BulkFormatStatus to shared infrastructure package"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 2: Abstract `BulkFormatEntity` base with `AnalysisBulkFormatEntity` and `DeckBulkFormatEntity`

**Files:**
- Create (move from anki): `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/BulkFormatEntity.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisBulkFormatEntity.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckBulkFormatEntity.java`
- Delete: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatEntity.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/BulkFormatKeys.java`

**Interfaces:**
- Produces:
  - Abstract `com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity` with mapped fields `pk`, `sk`, `status`, `createdAt`, `lastUpdatedAt`, `totalNotes`, `completedNotes`, `attempts`, `reformatAlreadyFormatted`, `statusBulkFormatIndexGsiPk`, `statusBulkFormatIndexGsiSk`; `protected void initialize(String pk, String sk, boolean reformatAlreadyFormatted)`; `public abstract UUID getOwnerId()`; `setStatus(BulkFormatStatus)`; `@NoArgsConstructor`.
  - `AnalysisBulkFormatEntity extends BulkFormatEntity`: ctor `(UUID analysisId, boolean reformatAlreadyFormatted)`, `getAnalysisId()`, `getOwnerId()`.
  - `DeckBulkFormatEntity extends BulkFormatEntity`: ctor `(UUID deckId, boolean reformatAlreadyFormatted)`, `getDeckId()`, `getOwnerId()`.
  - `BulkFormatKeys.deckBulkFormatSk()` → `"META#BULKFORMAT#DECK#"`.

- [ ] **Step 1: Rewrite the abstract base**

Replace `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatEntity.java` with the abstract class at `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/BulkFormatEntity.java`:

```java
package com.felixkroemer.smort.infrastructure.dynamodb;

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
public abstract class BulkFormatEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  @Getter(onMethod_ = @DynamoDbSecondaryPartitionKey(indexNames = "StatusBulkFormatIndex"))
  private String statusBulkFormatIndexGsiPk;

  @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "StatusBulkFormatIndex"))
  private String statusBulkFormatIndexGsiSk;

  private BulkFormatStatus status;
  private Instant createdAt;
  private Instant lastUpdatedAt;
  private int totalNotes;
  private int completedNotes;
  private int attempts;
  private boolean reformatAlreadyFormatted;

  public abstract UUID getOwnerId();

  protected void initialize(String pk, String sk, boolean reformatAlreadyFormatted) {
    this.pk = pk;
    this.sk = sk;
    this.status = BulkFormatStatus.PENDING;
    this.createdAt = Instant.now();
    this.lastUpdatedAt = Instant.now();
    this.attempts = 0;
    this.reformatAlreadyFormatted = reformatAlreadyFormatted;
    updateGsiKeys();
  }

  public void setStatus(BulkFormatStatus status) {
    this.status = status;
    updateGsiKeys();
  }

  private void updateGsiKeys() {
    this.statusBulkFormatIndexGsiPk = status.name();
    this.statusBulkFormatIndexGsiSk = Instant.now().toString();
  }
}
```

Delete the old `infrastructure/dynamodb/anki/BulkFormatEntity.java`. Add `import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;` and `import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;` where needed (see steps below; the base file above already compiles).

- [ ] **Step 2: Create `AnalysisBulkFormatEntity`**

`src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisBulkFormatEntity.java`:

```java
package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.UUID;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
@NoArgsConstructor
public class AnalysisBulkFormatEntity extends BulkFormatEntity {

  public AnalysisBulkFormatEntity(UUID analysisId, boolean reformatAlreadyFormatted) {
    initialize(AnalysisKeys.analysisPk(analysisId), BulkFormatKeys.bulkFormatSk(), reformatAlreadyFormatted);
  }

  public UUID getAnalysisId() {
    return UUID.fromString(pk.substring("ANALYSIS#".length()));
  }

  @Override
  public UUID getOwnerId() {
    return getAnalysisId();
  }
}
```

- [ ] **Step 3: Create `DeckBulkFormatEntity`**

`src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckBulkFormatEntity.java`:

```java
package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.UUID;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
@NoArgsConstructor
public class DeckBulkFormatEntity extends BulkFormatEntity {

  public DeckBulkFormatEntity(UUID deckId, boolean reformatAlreadyFormatted) {
    initialize(DeckKeys.deckPk(deckId), BulkFormatKeys.deckBulkFormatSk(), reformatAlreadyFormatted);
  }

  public UUID getDeckId() {
    return UUID.fromString(pk.substring("DECK#".length()));
  }

  @Override
  public UUID getOwnerId() {
    return getDeckId();
  }
}
```

- [ ] **Step 4: Add the deck sort key**

`src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/BulkFormatKeys.java` — add:

```java
public static String deckBulkFormatSk() {
  return "META#BULKFORMAT#DECK#";
}
```

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java/com/felixkroemer/smort/infrastructure/dynamodb
git commit -m "feat: add abstract BulkFormatEntity with analysis and deck subtypes"
```

Note in the report: compilation skipped per AGENTS.md. (The old `BulkFormatEntity` constructor is now gone; `BulkFormatService` still references it and is fixed in Task 5.)

---

### Task 3: Repository and config for both job types

**Files:**
- Move: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepository.java` → `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/BulkFormatRepository.java` (package becomes `com.felixkroemer.smort.infrastructure.dynamodb`; it manages both job types and must be shared, matching every reference in this plan)
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java`

**Interfaces:**
- Consumes: `BulkFormatEntity` (abstract, Task 2), `AnalysisBulkFormatEntity`, `DeckBulkFormatEntity`, `BulkFormatKeys.bulkFormatSk()`, `BulkFormatKeys.deckBulkFormatSk()`.
- Produces (used by Tasks 4, 5, 7, 9):
  - `Optional<AnalysisBulkFormatEntity> findBulkFormatByAnalysisId(UUID)`
  - `Optional<DeckBulkFormatEntity> findBulkFormatByDeckId(UUID)`
  - `List<BulkFormatEntity> findAllActive()`
  - `void save(BulkFormatEntity)` — dispatches by concrete type, keeps the cancellation conditional write
  - `void delete(UUID analysisId)`, `void deleteDeckJob(UUID deckId)`

- [ ] **Step 1: Move and rewrite `BulkFormatRepository`**

`git mv src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepository.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/BulkFormatRepository.java`, then set the package declaration to `com.felixkroemer.smort.infrastructure.dynamodb` and use the code below (imports unchanged in content):

```java
package com.felixkroemer.smort.infrastructure.dynamodb;

import com.felixkroemer.smort.common.exception.BulkFormatCancelledException;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BulkFormatRepository {

  private final DynamoDbTable<AnalysisBulkFormatEntity> analysisBulkFormatTable;
  private final DynamoDbTable<DeckBulkFormatEntity> deckBulkFormatTable;
  private final DynamoDbIndex<AnalysisBulkFormatEntity> statusAnalysisBulkFormatIndex;
  private final DynamoDbIndex<DeckBulkFormatEntity> statusDeckBulkFormatIndex;

  public Optional<AnalysisBulkFormatEntity> findBulkFormatByAnalysisId(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(BulkFormatKeys.bulkFormatSk())
            .build();
    return Optional.ofNullable(analysisBulkFormatTable.getItem(key));
  }

  public Optional<DeckBulkFormatEntity> findBulkFormatByDeckId(UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(BulkFormatKeys.deckBulkFormatSk())
            .build();
    return Optional.ofNullable(deckBulkFormatTable.getItem(key));
  }

  public List<BulkFormatEntity> findAllActive() {
    return Stream.of(BulkFormatStatus.IN_PROGRESS, BulkFormatStatus.WAITING_RETRY)
        .flatMap(
            status ->
                Stream.concat(
                    queryIndex(statusAnalysisBulkFormatIndex, status, BulkFormatKeys.bulkFormatSk()),
                    queryIndex(statusDeckBulkFormatIndex, status, BulkFormatKeys.deckBulkFormatSk())))
        .toList();
  }

  private <T extends BulkFormatEntity> Stream<BulkFormatEntity> queryIndex(
      DynamoDbIndex<T> index, BulkFormatStatus status, String sk) {
    return index
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.keyEqualTo(Key.builder().partitionValue(status.name()).build()))
                .filterExpression(
                    Expression.builder()
                        .expression("#sk = :sk")
                        .expressionNames(Map.of("#sk", "sk"))
                        .expressionValues(Map.of(":sk", AttributeValue.fromS(sk)))
                        .build())
                .build())
        .stream()
        .flatMap(page -> page.items().stream());
  }

  public void save(BulkFormatEntity entity) {
    if (entity instanceof AnalysisBulkFormatEntity analysisJob) {
      save(analysisBulkFormatTable, analysisJob);
    } else if (entity instanceof DeckBulkFormatEntity deckJob) {
      save(deckBulkFormatTable, deckJob);
    } else {
      throw new IllegalArgumentException(
          "Unsupported bulk format entity type: " + entity.getClass().getName());
    }
  }

  private <T extends BulkFormatEntity> void save(DynamoDbTable<T> table, T entity) {
    try {
      table.putItem(
          PutItemEnhancedRequest.<T>builder((Class<T>) entity.getClass())
              .item(entity)
              .conditionExpression(
                  Expression.builder()
                      .expression(
                          "attribute_not_exists(#status)"
                              + " OR (:newCreatedAt = #createdAt AND #status <> :cancelled)"
                              + " OR :newCreatedAt > #createdAt")
                      .putExpressionName("#status", "status")
                      .putExpressionName("#createdAt", "createdAt")
                      .putExpressionValue(
                          ":cancelled", AttributeValue.fromS(BulkFormatStatus.CANCELLED.name()))
                      .putExpressionValue(
                          ":newCreatedAt",
                          AttributeValue.fromS(entity.getCreatedAt().toString()))
                      .build())
              .build());
    } catch (ConditionalCheckFailedException e) {
      throw new BulkFormatCancelledException(
          "Bulk format was cancelled. ownerId={}", entity.getOwnerId());
    }
  }

  public void delete(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(BulkFormatKeys.bulkFormatSk())
            .build();
    analysisBulkFormatTable.deleteItem(key);
  }

  public void deleteDeckJob(UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(BulkFormatKeys.deckBulkFormatSk())
            .build();
    deckBulkFormatTable.deleteItem(key);
  }
}
```

Note: the `(Class<T>) entity.getClass()` cast is an unchecked cast — required because `getClass()` returns `Class<? extends BulkFormatEntity>` while `PutItemEnhancedRequest.builder(Class<T>)` needs `Class<T>`. This mirrors the generic nature of the two table beans.

- [ ] **Step 2: Wire both tables and indexes in `DynamoDbClientConfig`**

`src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java` — update imports and beans:

```java
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckBulkFormatEntity;
```

Replace the existing `bulkFormatTable`/`statusBulkFormatIndex` beans:

```java
@Bean
DynamoDbTable<AnalysisBulkFormatEntity> bulkFormatTable(DynamoDbEnhancedClient enhancedClient) {
  return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(AnalysisBulkFormatEntity.class));
}

@Bean
DynamoDbTable<DeckBulkFormatEntity> deckBulkFormatTable(DynamoDbEnhancedClient enhancedClient) {
  return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(DeckBulkFormatEntity.class));
}

@Bean
DynamoDbIndex<AnalysisBulkFormatEntity> statusBulkFormatIndex(
    DynamoDbTable<AnalysisBulkFormatEntity> bulkFormatTable) {
  return bulkFormatTable.index("StatusBulkFormatIndex");
}

@Bean
DynamoDbIndex<DeckBulkFormatEntity> statusDeckBulkFormatIndex(
    DynamoDbTable<DeckBulkFormatEntity> deckBulkFormatTable) {
  return deckBulkFormatTable.index("StatusBulkFormatIndex");
}
```

Remove the now-unused `import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb
git commit -m "feat: support deck bulk format jobs in repository and config"
```

Note in the report: compilation skipped per AGENTS.md; flag for the human that `StatusBulkFormatIndex` must project all attributes for the `sk` filter expression to work (the spec assumption).

---

### Task 4: `BulkFormatEngine` in `domain/common` and move shared domain types

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/common/BulkFormatEngine.java`
- Move: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormat.java` → `src/main/java/com/felixkroemer/smort/domain/common/BulkFormat.java`
- Move: `src/main/java/com/felixkroemer/smort/domain/anki/mapping/BulkFormatEntityMapper.java` → `src/main/java/com/felixkroemer/smort/domain/common/mapping/BulkFormatEntityMapper.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java` (import)
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/mapping/BulkFormatRestMapper.java` (import)

**Interfaces:**
- Consumes: `BulkFormatRepository.save(BulkFormatEntity)`, `BulkFormatCancelledException`, `AsyncTaskExecutor` bean named `bulkFormatTaskExecutor` (from `BulkFormatConfig`).
- Produces (used by Tasks 5, 7):
  - `void dispatch(BulkFormatEntity job, Runnable task)`
  - `<T> void process(BulkFormatEntity job, List<T> items, ItemProcessor<T> processor)`
  - `void cancel(BulkFormatEntity job)`
  - `void assertNoActiveJob(Optional<? extends BulkFormatEntity> existing)`
  - `@FunctionalInterface ItemProcessor<T> { void process(T item) throws Exception; }`
  - Constants `MAX_ATTEMPTS`, `MAX_RECENT_FAILED` (both `2`)
  - `BulkFormat` (domain object) in `domain/common`, `BulkFormatEntityMapper` in `domain/common/mapping` (MapStruct, componentModel spring) mapping `BulkFormat toBulkFormat(BulkFormatEntity)`.

- [ ] **Step 1: Create `BulkFormatEngine`**

`src/main/java/com/felixkroemer/smort/domain/common/BulkFormatEngine.java`:

```java
package com.felixkroemer.smort.domain.common;

import com.felixkroemer.smort.common.exception.BulkFormatCancelledException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkFormatEngine {

  public static final int MAX_RECENT_FAILED = 2;
  public static final int MAX_ATTEMPTS = 2;

  private final BulkFormatRepository bulkFormatRepository;
  private final AsyncTaskExecutor bulkFormatTaskExecutor;

  @FunctionalInterface
  public interface ItemProcessor<T> {
    void process(T item) throws Exception;
  }

  public void assertNoActiveJob(Optional<? extends BulkFormatEntity> existing) {
    existing.ifPresent(
        job -> {
          if (isActive(job)) {
            throw new SmortException(
                "Bulk format already in progress. ownerId={}", job.getOwnerId());
          }
        });
  }

  public void dispatch(BulkFormatEntity job, Runnable task) {
    bulkFormatTaskExecutor.execute(
        () -> {
          try {
            task.run();
          } catch (BulkFormatCancelledException e) {
            log.info("Bulk format cancelled. ownerId={}", job.getOwnerId());
          } catch (Exception e) {
            log.error(
                "Unexpected error during bulk format processing. ownerId={}",
                job.getOwnerId(),
                e);
          }
        });
  }

  public void cancel(BulkFormatEntity job) {
    if (isActive(job)) {
      job.setStatus(BulkFormatStatus.CANCELLED);
      bulkFormatRepository.save(job);
    }
  }

  public <T> void process(
      BulkFormatEntity job, List<T> items, ItemProcessor<T> itemProcessor) {
    int processed = 0;
    int failed = 0;
    int consecutiveFailed = 0;
    int attempts = job.getAttempts() + 1;

    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setAttempts(attempts);
    bulkFormatRepository.save(job);

    for (var item : items) {
      try {
        itemProcessor.process(item);
        processed++;
        consecutiveFailed = 0;
        job.setCompletedNotes(job.getCompletedNotes() + 1);
      } catch (Exception e) {
        failed++;
        consecutiveFailed++;
        log.warn(
            "Failed to format item during bulk format. ownerId={}", job.getOwnerId(), e);
        if (consecutiveFailed >= MAX_RECENT_FAILED) {
          log.warn(
              "Hit consecutive failed limit while processing bulk format. ownerId={}",
              job.getOwnerId());
          break;
        }
        continue;
      }

      job.setLastUpdatedAt(Instant.now());
      bulkFormatRepository.save(job);
    }

    handleProcessNotesResult(job, processed, failed);
  }

  private void handleProcessNotesResult(BulkFormatEntity job, int processed, int failed) {
    var ownerId = job.getOwnerId();
    if (failed == 0) {
      job.setStatus(BulkFormatStatus.COMPLETED);
      job.setLastUpdatedAt(Instant.now());
      bulkFormatRepository.save(job);

      log.info(
          "Bulk format complete. ownerId={}, processed={}, failed={}",
          ownerId,
          processed,
          failed);
    } else {
      if (job.getAttempts() >= MAX_ATTEMPTS) {
        job.setStatus(BulkFormatStatus.FAILED);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);

        log.warn(
            "Bulk format reached max attempts. Setting to FAILED. ownerId={}, processed={}, failed={}",
            ownerId,
            processed,
            failed);
      } else {
        job.setStatus(BulkFormatStatus.WAITING_RETRY);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);
        log.info(
            "Bulk format had errors. Will resume later. ownerId={}, processed={}, failed={}, attempts={}",
            ownerId,
            processed,
            failed,
            job.getAttempts());
      }
    }
  }

  private static boolean isActive(BulkFormatEntity job) {
    return job.getStatus() == BulkFormatStatus.PENDING
        || job.getStatus() == BulkFormatStatus.IN_PROGRESS
        || job.getStatus() == BulkFormatStatus.WAITING_RETRY;
  }
}
```

- [ ] **Step 2: Move `BulkFormat` and `BulkFormatEntityMapper`**

Move `domain/anki/BulkFormat.java` → `domain/common/BulkFormat.java`. Change only the package declaration to `com.felixkroemer.smort.domain.common` (its `BulkFormatStatus` import was already fixed in Task 1).

Move `domain/anki/mapping/BulkFormatEntityMapper.java` → `domain/common/mapping/BulkFormatEntityMapper.java`. Change the package declaration to `com.felixkroemer.smort.domain.common.mapping` and update imports:
- `import com.felixkroemer.smort.domain.anki.BulkFormat;` → `import com.felixkroemer.smort.domain.common.BulkFormat;`
- `import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;` → `import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;`

- [ ] **Step 3: Fix remaining importers of the moved types**

- `domain/anki/AnalysisService.java`: `import com.felixkroemer.smort.domain.anki.mapping.BulkFormatEntityMapper;` → `import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;`
- `application/anki/mapping/BulkFormatRestMapper.java`: `import com.felixkroemer.smort.domain.anki.BulkFormat;` → `import com.felixkroemer.smort.domain.common.BulkFormat;` (and it already imports the shared `BulkFormatStatus` from Task 1).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/common
git add src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java
git add src/main/java/com/felixkroemer/smort/application/anki/mapping/BulkFormatRestMapper.java
git rm src/main/java/com/felixkroemer/smort/domain/anki/BulkFormat.java
git rm src/main/java/com/felixkroemer/smort/domain/anki/mapping/BulkFormatEntityMapper.java
git commit -m "feat: extract shared BulkFormatEngine and move bulk format domain types to common"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 5: Refactor `BulkFormatService` to use the engine and `AnalysisBulkFormatEntity`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java`

**Interfaces:**
- Consumes: `BulkFormatEngine` (Task 4), `BulkFormatEntityMapper` (Task 4), `BulkFormatRepository` (Task 3), `AnalysisBulkFormatEntity` (Task 2).
- Produces: unchanged public API `startBulkFormat(UUID, boolean)`, `resumeBulkFormat(AnalysisBulkFormatEntity)`, `cancelBulkFormat(UUID)`, `getJobStatus(UUID)`.

- [ ] **Step 1: Rewrite `BulkFormatService`**

Replace the body of `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java` with:

```java
package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.LogSeverity;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.mapping.DerivedNoteEntityMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.domain.common.BulkFormatEngine;
import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkFormatService {

  private final BulkFormatRepository bulkFormatRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final AnalysisService analysisService;
  private final ChatService chatService;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;
  private final DerivedNoteEntityMapper derivedNoteEntityMapper;
  private final BulkFormatEngine bulkFormatEngine;

  public void startBulkFormat(UUID analysisId, boolean reformatAlreadyFormatted) {
    bulkFormatEngine.assertNoActiveJob(
        bulkFormatRepository.findBulkFormatByAnalysisId(analysisId));

    var notes = analysisService.getNotes(analysisId);
    var existingDerivedNotes =
        analysisService.getDerivedNotes(analysisId).stream()
            .collect(
                Collectors.toMap(
                    DerivedNoteEntity::getNoteId, Function.identity(), (first, second) -> first));

    var job = new AnalysisBulkFormatEntity(analysisId, reformatAlreadyFormatted);
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
    bulkFormatEngine.dispatch(job, () -> processNotes(job, notesToProcess));
  }

  public void resumeBulkFormat(AnalysisBulkFormatEntity bulkFormatEntity) {
    bulkFormatEngine.dispatch(bulkFormatEntity, () -> processNotes(bulkFormatEntity));
  }

  public void cancelBulkFormat(UUID analysisId) {
    var job =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .orElseThrow(
                () -> new NotFoundException("No bulk format job found. analysisId={}", analysisId));
    bulkFormatEngine.cancel(job);
  }

  private void processNotes(AnalysisBulkFormatEntity job) {
    var notes = analysisService.getNotes(job.getAnalysisId());
    var existingDerivedNotes =
        analysisService.getDerivedNotes(job.getAnalysisId()).stream()
            .collect(
                Collectors.toMap(
                    DerivedNoteEntity::getNoteId, Function.identity(), (first, second) -> first));
    var notesToProcess = getNotesToProcess(notes, existingDerivedNotes, job);
    processNotes(job, notesToProcess);
  }

  private void processNotes(
      AnalysisBulkFormatEntity job, List<NoteToProcess> notesToProcess) {
    var analysisId = job.getAnalysisId();
    Analysis analysis;
    try {
      analysis = analysisService.getAnalysis(analysisId);
    } catch (NotFoundException e) {
      throw e.withSeverity(LogSeverity.ERROR);
    }

    bulkFormatEngine.process(
        job,
        notesToProcess,
        noteToProcess -> {
          var noteEntity = noteToProcess.ankiNote();
          var existingDerivedNote = noteToProcess.existingDerivedNote();
          var content =
              existingDerivedNote
                  .map(DerivedNoteEntity::getContent)
                  .orElse(noteEntity.getContent());
          var noteSchema = chatService.formatNote(content, analysis.getFormatInstructions());
          var derivedNote =
              existingDerivedNote
                  .map(
                      d -> {
                        d.setFront(noteSchema.front());
                        d.setBack(noteSchema.back());
                        d.setLastFormattedAt(Optional.of(Instant.now()));
                        return d;
                      })
                  .orElseGet(
                      () ->
                          derivedNoteEntityMapper.toDerivedNoteEntity(
                              analysisId, noteEntity.getId(), noteSchema));
          derivedNoteRepository.save(derivedNote);
        });
  }

  private List<NoteToProcess> getNotesToProcess(
      List<AnkiNote> notes,
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
        .map(
            note ->
                new NoteToProcess(
                    note, Optional.ofNullable(existingDerivedNotes.get(note.getId()))))
        .toList();
  }

  private record NoteToProcess(
      AnkiNote ankiNote, Optional<DerivedNoteEntity> existingDerivedNote) {}

  public BulkFormat getJobStatus(UUID analysisId) {
    return bulkFormatRepository
        .findBulkFormatByAnalysisId(analysisId)
        .map(bulkFormatEntityMapper::toBulkFormat)
        .orElseThrow(
            () -> new NotFoundException("No bulk format job found. analysisId={}", analysisId));
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java
git commit -m "refactor: delegate analysis bulk format to shared engine"
```

Note in the report: compilation skipped per AGENTS.md. (`BulkFormatCron` still calls `resumeBulkFormat(BulkFormatEntity)`; fixed in Task 9.)

---

### Task 6: Add `lastFormattedAt` to `NoteEntity`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/NoteEntity.java`

**Interfaces:**
- Produces: `Optional<Instant> getLastFormattedAt()` / `setLastFormattedAt(Optional<Instant>)` on `NoteEntity`, defaulting to `Optional.empty()`, persisted via `OptionalInstantConverter`.

- [ ] **Step 1: Add the field**

`src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/NoteEntity.java` — add imports:

```java
import com.felixkroemer.smort.infrastructure.dynamodb.OptionalInstantConverter;
import java.time.Instant;
import java.util.Optional;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
```

and the field (after `back`):

```java
@Getter(onMethod_ = @DynamoDbConvertedBy(OptionalInstantConverter.class))
private Optional<Instant> lastFormattedAt = Optional.empty();
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/NoteEntity.java
git commit -m "feat: track lastFormattedAt on deck NoteEntity"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 7: Add `DeckBulkFormatService`

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java`

**Interfaces:**
- Consumes: `BulkFormatEngine` (Task 4), `BulkFormatEntityMapper` (Task 4), `BulkFormatRepository` (Task 3), `DeckBulkFormatEntity` (Task 2), `NoteEntity.lastFormattedAt` (Task 6), `ChatService.formatNote(Map<String,String>, Optional<String>)`.
- Produces (used by Task 8 and Task 9):
  - `void startBulkFormat(UUID deckId, boolean reformatAlreadyFormatted)`
  - `void resumeBulkFormat(DeckBulkFormatEntity)`
  - `void cancelBulkFormat(UUID deckId)`
  - `BulkFormat getJobStatus(UUID deckId)`

- [ ] **Step 1: Create the service**

`src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java`:

```java
package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.LogSeverity;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.domain.common.BulkFormatEngine;
import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckBulkFormatService {

  private final BulkFormatRepository bulkFormatRepository;
  private final DeckRepository deckRepository;
  private final ChatService chatService;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;
  private final BulkFormatEngine bulkFormatEngine;

  public void startBulkFormat(UUID deckId, boolean reformatAlreadyFormatted) {
    bulkFormatEngine.assertNoActiveJob(bulkFormatRepository.findBulkFormatByDeckId(deckId));

    var notes = deckRepository.findNotesByDeckId(deckId);
    var job = new DeckBulkFormatEntity(deckId, reformatAlreadyFormatted);
    var notesToProcess = getNotesToProcess(notes, job);

    if (notesToProcess.isEmpty()) {
      throw new SmortException(
          HttpStatus.BAD_REQUEST,
          LogSeverity.INFO,
          "No notes to format for deck. deckId={}",
          deckId);
    }

    job.setTotalNotes(notesToProcess.size());
    bulkFormatRepository.save(job);
    bulkFormatEngine.dispatch(job, () -> processNotes(job, notesToProcess));
  }

  public void resumeBulkFormat(DeckBulkFormatEntity bulkFormatEntity) {
    bulkFormatEngine.dispatch(bulkFormatEntity, () -> processNotes(bulkFormatEntity));
  }

  public void cancelBulkFormat(UUID deckId) {
    var job =
        bulkFormatRepository
            .findBulkFormatByDeckId(deckId)
            .orElseThrow(
                () -> new NotFoundException("No bulk format job found. deckId={}", deckId));
    bulkFormatEngine.cancel(job);
  }

  private void processNotes(DeckBulkFormatEntity job) {
    var notes = deckRepository.findNotesByDeckId(job.getDeckId());
    var notesToProcess = getNotesToProcess(notes, job);
    processNotes(job, notesToProcess);
  }

  private void processNotes(DeckBulkFormatEntity job, List<NoteEntity> notesToProcess) {
    bulkFormatEngine.process(
        job,
        notesToProcess,
        note -> {
          var noteSchema = chatService.formatNote(note.getFront(), note.getBack(), Optional.empty());
          note.setFront(noteSchema.front());
          note.setBack(noteSchema.back());
          note.setLastFormattedAt(Optional.of(Instant.now()));
          deckRepository.saveNote(note);
        });
  }

  private List<NoteEntity> getNotesToProcess(List<NoteEntity> notes, BulkFormatEntity job) {
    return notes.stream()
        .filter(
            note -> {
              var lastFormattedAt = note.getLastFormattedAt();
              if (lastFormattedAt.isEmpty()) {
                return true;
              }
              if (!job.isReformatAlreadyFormatted()) {
                return false;
              }
              return lastFormattedAt.get().isBefore(job.getCreatedAt());
            })
        .toList();
  }

  public BulkFormat getJobStatus(UUID deckId) {
    return bulkFormatRepository
        .findBulkFormatByDeckId(deckId)
        .map(bulkFormatEntityMapper::toBulkFormat)
        .orElseThrow(() -> new NotFoundException("No bulk format job found. deckId={}", deckId));
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java
git commit -m "feat: add deck bulk format service"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 8: Add `DeckController` bulk format endpoints

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`

**Interfaces:**
- Consumes: `DeckBulkFormatService` (Task 7), `BulkFormatResponse` and `BulkFormatRestMapper` (from `application/anki`).
- Produces: endpoints `POST /decks/{deckId}/format`, `GET /decks/{deckId}/format/status`, `POST /decks/{deckId}/format/cancel`.

- [ ] **Step 1: Add endpoints to `DeckController`**

`src/main/java/com/felixkroemer/smort/application/deck/DeckController.java` — add imports:

```java
import com.felixkroemer.smort.application.anki.dto.BulkFormatResponse;
import com.felixkroemer.smort.application.anki.mapping.BulkFormatRestMapper;
import com.felixkroemer.smort.domain.deck.DeckBulkFormatService;
import org.springframework.http.HttpStatus;
```

Add fields:

```java
private final DeckBulkFormatService deckBulkFormatService;
private final BulkFormatRestMapper bulkFormatRestMapper;
```

Add endpoints (after the existing `formatNote` mapping):

```java
@PostMapping("/{deckId}/format")
@ResponseStatus(HttpStatus.ACCEPTED)
public void startBulkFormat(
    @PathVariable UUID deckId,
    @RequestParam(defaultValue = "true") boolean reformatAlreadyFormatted) {
  deckBulkFormatService.startBulkFormat(deckId, reformatAlreadyFormatted);
}

@GetMapping("/{deckId}/format/status")
public BulkFormatResponse getBulkFormatStatus(@PathVariable UUID deckId) {
  return bulkFormatRestMapper.toBulkFormatResponse(deckBulkFormatService.getJobStatus(deckId));
}

@PostMapping("/{deckId}/format/cancel")
@ResponseStatus(HttpStatus.ACCEPTED)
public void cancelBulkFormat(@PathVariable UUID deckId) {
  deckBulkFormatService.cancelBulkFormat(deckId);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add deck bulk format endpoints"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 9: Route crashed-job resume in `BulkFormatCron`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/cron/BulkFormatCron.java`

**Interfaces:**
- Consumes: `BulkFormatRepository.findAllActive()` (Task 3), `BulkFormatService.resumeBulkFormat(AnalysisBulkFormatEntity)` (Task 5), `DeckBulkFormatService.resumeBulkFormat(DeckBulkFormatEntity)` (Task 7), `BulkFormatKeys.bulkFormatSk()`, `BulkFormatKeys.deckBulkFormatSk()`.
- Produces: routing that resumes analysis jobs via the analysis service and deck jobs via the deck service.

- [ ] **Step 1: Rewrite `BulkFormatCron`**

`src/main/java/com/felixkroemer/smort/domain/cron/BulkFormatCron.java`:

```java
package com.felixkroemer.smort.domain.cron;

import com.felixkroemer.smort.domain.anki.BulkFormatService;
import com.felixkroemer.smort.domain.deck.DeckBulkFormatService;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.BulkFormatKeys;
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

  private static final Duration CRASH_TIMEOUT = Duration.ofMinutes(2);

  private final BulkFormatRepository bulkFormatRepository;
  private final BulkFormatService bulkFormatService;
  private final DeckBulkFormatService deckBulkFormatService;

  @Scheduled(fixedDelayString = "${app.scheduling.bulk-format-delay}")
  public void resumeCrashedBulkFormats() {
    var allJobs = bulkFormatRepository.findAllActive();
    for (var job : allJobs) {
      if (Duration.between(job.getLastUpdatedAt(), Instant.now()).compareTo(CRASH_TIMEOUT) > 0) {
        log.warn(
            "Resuming IN_PROGRESS bulk format. ownerId={}, lastUpdate={}, attempts={}",
            job.getOwnerId(),
            job.getLastUpdatedAt(),
            job.getAttempts());
        try {
          resume(job);
        } catch (Exception e) {
          log.error("Failed to resume bulk format. ownerId={}", job.getOwnerId(), e);
        }
      }
    }
  }

  private void resume(BulkFormatEntity job) {
    if (BulkFormatKeys.bulkFormatSk().equals(job.getSk())) {
      bulkFormatService.resumeBulkFormat((AnalysisBulkFormatEntity) job);
    } else if (BulkFormatKeys.deckBulkFormatSk().equals(job.getSk())) {
      deckBulkFormatService.resumeBulkFormat((DeckBulkFormatEntity) job);
    } else {
      throw new IllegalArgumentException(
          "Unknown bulk format job sort key: " + job.getSk());
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/cron/BulkFormatCron.java
git commit -m "feat: route crashed bulk format resume by job type"
```

Note in the report: compilation skipped per AGENTS.md.

---

## Self-Review

**Spec coverage:**
- Two services + shared engine → Tasks 4, 5, 7
- Abstract `BulkFormatEntity`, two subclasses, own constructors, own `sk` → Task 2
- `BulkFormatStatus` shared location → Task 1
- Repository with `findBulkFormatByDeckId`, save dispatch, `findAllActive` by type → Task 3
- Config wiring → Task 3
- `NoteEntity.lastFormattedAt` → Task 6
- `BulkFormatService` refactor (behavior unchanged) → Task 5
- `DeckBulkFormatService` (default instructions, in-place mutation + save) → Task 7
- `DeckController` endpoints + reuse of `BulkFormatResponse`/`BulkFormatRestMapper` → Task 8
- Cron routing by `sk` → Task 9
- Moves of `BulkFormat`/`BulkFormatEntityMapper` to `domain/common` → Task 4

**Placeholder scan:** no TBDs; every code step contains full file content.

**Type consistency:** `save(BulkFormatEntity)` single dispatch used by engine/services; `resumeBulkFormat` takes the concrete subtype in each service; `getOwnerId()` used for logging in engine and cron; `findBulkFormatByDeckId`/`deleteDeckJob` names consistent across Tasks 3, 7, 8.
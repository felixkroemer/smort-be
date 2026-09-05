# Analyses-by-User Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `AnalysisService.getAnalyses()` return only the current (dummy) user's analyses by adding a `UserAnalysisIndex` GSI, mirroring `DeckService.getDecks()`.

**Architecture:** Add a `UserAnalysisIndex` global secondary index on `AnalysisMetaEntity` (partition key `USER#<userId>`, sort key `ANALYSIS#<analysisId>`), add a `userId` attribute, query the index by user in `AnalysisMetaRepository`, switch `getAnalyses()`/`createAnalysis()` to the hardcoded `"default"` user, and remove the now-dead `findAllAnalysisMetas()` scan.

**Tech Stack:** Java 17, Spring Boot, AWS SDK Enhanced DynamoDB (`@DynamoDbSecondaryPartitionKey`/`@DynamoDbSecondarySortKey`, `DynamoDbIndex`).

## Global Constraints

- Current user is hardcoded to the dummy string `"default"` (no auth), matching `DeckService.getDecks()`.
- New GSI is named `UserAnalysisIndex`, mirroring the existing `UserDeckIndex`.
- The `UserAnalysisIndex` GSI must be provisioned on `common-table` in DynamoDB — this is external/infra and out of scope for the code change.
- No tests are written (AGENTS.md: tests only when explicitly requested).
- Compilation/build is NOT run by the implementing agent (AGENTS.md: the human owns compilation and verifies later).
- All work is committed to the `feat/analyses-by-user` feature branch; main is never touched.
- GSI partition key format: `"USER#" + userId`. GSI sort key format: `"ANALYSIS#" + analysisId`.

---

### Task 1: Add GSI key helpers

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/partition/AnalysisKeys.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/MetaKeys.java`

**Interfaces:**
- Consumes: nothing (uses existing `java.util.UUID`).
- Produces:
  - `AnalysisKeys.userAnalysisIndexGsiPk(String userId)` → `String`, returns `"USER#" + userId`.
  - `MetaKeys.userAnalysisIndexGsiSk(UUID analysisId)` → `String`, returns `"ANALYSIS#" + analysisId`.

- [ ] **Step 1: Add the partition-key helper to `AnalysisKeys.java`**

Add after the existing `analysisPkPrefix()` method:

```java
  public static String userAnalysisIndexGsiPk(String userId) {
    return "USER#" + userId;
  }
```

- [ ] **Step 2: Add the sort-key helper to `MetaKeys.java`**

Add after the existing `userDeckIndexGsiSk(UUID deckId)` method:

```java
  public static String userAnalysisIndexGsiSk(UUID analysisId) {
    return "ANALYSIS#" + analysisId;
  }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/partition/AnalysisKeys.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/MetaKeys.java
git commit -m "feat: add UserAnalysisIndex GSI key helpers"
```

---

### Task 2: Add `userId` and GSI keys to `AnalysisMetaEntity`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaEntity.java`

**Interfaces:**
- Consumes: `AnalysisKeys.userAnalysisIndexGsiPk(String)`, `MetaKeys.userAnalysisIndexGsiSk(UUID)` (from Task 1), existing `AnalysisKeys.analysisPk(UUID)`, `MetaKeys.metaSk()`.
- Produces: `AnalysisMetaEntity(UUID analysisId, String userId, AnalysisStatus status)` constructor and new fields `userId`, `userAnalysisIndexGsiPk`, `userAnalysisIndexGsiSk`.

- [ ] **Step 1: Add GSI key fields and `userId`**

After the `sk` field (line ~24), insert:

```java
  @Getter(onMethod_ = @DynamoDbSecondaryPartitionKey(indexNames = "UserAnalysisIndex"))
  private String userAnalysisIndexGsiPk;

  @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "UserAnalysisIndex"))
  private String userAnalysisIndexGsiSk;

  private String userId;
```

- [ ] **Step 2: Update the constructor signature and body**

Change the constructor to accept `userId` and populate the new fields:

```java
  public AnalysisMetaEntity(UUID analysisId, String userId, AnalysisStatus status) {
    this.pk = AnalysisKeys.analysisPk(analysisId);
    this.sk = MetaKeys.metaSk();
    this.userAnalysisIndexGsiPk = AnalysisKeys.userAnalysisIndexGsiPk(userId);
    this.userAnalysisIndexGsiSk = MetaKeys.userAnalysisIndexGsiSk(analysisId);
    this.userId = userId;
    this.status = status;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaEntity.java
git commit -m "feat: add userId and GSI keys to AnalysisMetaEntity"
```

---

### Task 3: Wire the `userAnalysisIndex` bean

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java`

**Interfaces:**
- Consumes: the existing `analysisMetaTable` bean (`DynamoDbTable<AnalysisMetaEntity>`), `DynamoDbIndex` import (already present).
- Produces: `@Bean DynamoDbIndex<AnalysisMetaEntity> userAnalysisIndex(DynamoDbTable<AnalysisMetaEntity> analysisMetaTable)`.

- [ ] **Step 1: Add the index bean**

Add after the existing `userDeckIndex` bean (after line ~84):

```java
  @Bean
  DynamoDbIndex<AnalysisMetaEntity> userAnalysisIndex(
      DynamoDbTable<AnalysisMetaEntity> analysisMetaTable) {
    return analysisMetaTable.index("UserAnalysisIndex");
  }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java
git commit -m "feat: wire UserAnalysisIndex DynamoDB index bean"
```

---

### Task 4: Query by user in `AnalysisMetaRepository` and remove the scan

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaRepository.java`

**Interfaces:**
- Consumes: the `userAnalysisIndex` bean from Task 3 (injected as `private final DynamoDbIndex<AnalysisMetaEntity> userAnalysisIndex`), `AnalysisKeys.userAnalysisIndexGsiPk(String)`, `AnalysisStatus.MARKED_FOR_DELETION`, existing wildcard DynamoDB imports.
- Produces: `List<AnalysisMetaEntity> findAnalysisMetasByUserId(String userId)`.

- [ ] **Step 1: Inject the index**

Add the field to the class:

```java
  private final DynamoDbTable<AnalysisMetaEntity> analysisMetaTable;
  private final DynamoDbIndex<AnalysisMetaEntity> userAnalysisIndex;
```

- [ ] **Step 2: Add `findAnalysisMetasByUserId`**

Add this method (and remove the old `findAllAnalysisMetas()` scan in Step 3):

```java
  public List<AnalysisMetaEntity> findAnalysisMetasByUserId(String userId) {
    var condition =
        QueryConditional.keyEqualTo(
            Key.builder().partitionValue(AnalysisKeys.userAnalysisIndexGsiPk(userId)).build());

    Expression filter =
        Expression.builder()
            .expression("#status <> :status")
            .expressionNames(Map.of("#status", "status"))
            .expressionValues(
                Map.of(
                    ":status", AttributeValue.fromS(AnalysisStatus.MARKED_FOR_DELETION.toString())))
            .build();

    return userAnalysisIndex
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .filterExpression(filter)
                .build())
        .stream()
        .flatMap(page -> page.items().stream())
        .toList();
  }
```

- [ ] **Step 3: Remove the dead `findAllAnalysisMetas()` scan**

Delete the entire `findAllAnalysisMetas()` method (the table `scan(...)` one). The `ScanEnhancedRequest` import may become unused — remove it if it is no longer referenced anywhere in the file. Keep `Map`, `AttributeValue`, `Expression`, `Key`, `QueryConditional`, `QueryEnhancedRequest` (all still used by the new method and `findAnalysisMetaByAnalysisId`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaRepository.java
git commit -m "feat: query analyses by user via UserAnalysisIndex; remove scan"
```

---

### Task 5: Update `AnalysisService` to use the current user

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java`

**Interfaces:**
- Consumes: `AnalysisMetaRepository.findAnalysisMetasByUserId(String)` (from Task 4), `AnalysisMetaEntity(UUID, String, AnalysisStatus)` constructor (from Task 2).
- Produces: updated `createAnalysis()` and `getAnalyses()` behavior.

- [ ] **Step 1: Pass the dummy user in `createAnalysis()`**

Change:

```java
    var analysis = new AnalysisMetaEntity(UUID.randomUUID(), AnalysisStatus.NEW);
```

to:

```java
    var analysis = new AnalysisMetaEntity(UUID.randomUUID(), "default", AnalysisStatus.NEW);
```

- [ ] **Step 2: Switch `getAnalyses()` to the user query**

Change:

```java
  public List<Analysis> getAnalyses() {
    return analysisMetaRepository.findAllAnalysisMetas().stream()
```

to:

```java
  public List<Analysis> getAnalyses() {
    return analysisMetaRepository.findAnalysisMetasByUserId("default").stream()
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java
git commit -m "feat: fetch analyses of current user in getAnalyses"
```

---

### Task 6: Final verification and push

**Files:**
- None (verification only).

**Interfaces:**
- Consumes: everything from Tasks 1–5.

- [ ] **Step 1: Confirm all references are consistent**

Run `grep -rn "findAllAnalysisMetas\|new AnalysisMetaEntity("` across `src/main/java`. Expected: no remaining `findAllAnalysisMetas()` references; exactly one `new AnalysisMetaEntity(...)` call with three arguments (`UUID.randomUUID(), "default", AnalysisStatus.NEW`).

- [ ] **Step 2: Push the feature branch**

```bash
git push -u origin feat/analyses-by-user
```

Note: do NOT merge into main. Stop after pushing and let the human review.
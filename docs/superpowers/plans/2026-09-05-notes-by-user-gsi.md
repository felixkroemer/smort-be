# UserNoteIndex GSI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `UserNoteIndex` global secondary index on `NoteEntity` so all notes for a user can be fetched in one query, exposed via a `DeckService.getAllNotes()` method.

**Architecture:** Mirror the existing `UserDeckIndex` / `UserAnalysisIndex` patterns. `NoteEntity` gains a `userId` and two GSI-key fields. The index is queried by the current (hardcoded `"default"`) user through a repository method, and a service method exposes the result.

**Tech Stack:** Java 17, Spring, AWS SDK DynamoDB Enhanced Client (`DynamoDbTable`, `DynamoDbIndex`, mapper annotations), MapStruct, Lombok, Maven.

## Global Constraints

- No auth yet — the current user is the hardcoded dummy user `"default"` everywhere.
- Do not write tests (per AGENTS.md — tests only when explicitly requested).
- Do not run or debug the build (`./mvnw compile`, `./mvnw test`); compilation is owned by the human and skipped.
- GSI sort key is flat `NOTE#<noteId>` (per-deck note queries already exist via the primary key).
- All keys use the existing string-prefix convention (`USER#`, `NOTE#`).
- Commit each task's changes to the feature branch `feat/notes-by-user-gsi`.

---

### Task 1: Add GSI key fields and userId to `NoteEntity`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/NoteEntity.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `NoteEntity` gains fields `userId`, `userNoteIndexGsiPk`, `userNoteIndexGsiSk` (with Lombok `@Getter`/`@Setter`); these are mapped by `NoteEntityMapper` in Task 2.

- [ ] **Step 1: Add the fields and index annotations**

Add two fields annotated for the new `UserNoteIndex` GSI and a `userId` field. Add the two imports from the mapper annotations package if not already present (the class currently imports only `DynamoDbBean`, `DynamoDbConvertedBy`, `DynamoDbPartitionKey`, `DynamoDbSortKey`):

```java
@Getter(onMethod_ = @DynamoDbSecondaryPartitionKey(indexNames = "UserNoteIndex"))
private String userNoteIndexGsiPk;

@Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "UserNoteIndex"))
private String userNoteIndexGsiSk;

private String userId;
```

Add the import:
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/NoteEntity.java
git commit -m "feat: add UserNoteIndex GSI key fields to NoteEntity"
```

---

### Task 2: Add userNoteIndex pk method to `NoteKeys`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/NoteKeys.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `NoteKeys.userNoteIndexGsiPk(String userId)` returning `"USER#" + userId`, consumed by `NoteEntityMapper` (Task 3) and `DeckRepository.findNotesByUserId` (Task 5).

- [ ] **Step 1: Add the method**

```java
public static String userNoteIndexGsiPk(String userId) {
  return "USER#" + userId;
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/NoteKeys.java
git commit -m "feat: add userNoteIndex pk method to NoteKeys"
```

---

### Task 3: Thread userId through `NoteEntityMapper`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/mapping/NoteEntityMapper.java`

**Interfaces:**
- Consumes: `NoteKeys.userNoteIndexGsiPk(String userId)` (Task 2), `NoteKeys.noteSk(UUID noteId)` (existing), `DeckKeys.deckPk(UUID deckId)` (existing).
- Produces: `NoteEntity toNoteEntity(UUID deckId, UUID noteId, NoteSchema noteSchema, String userId)` — signature consumed by all call sites in Task 4.

- [ ] **Step 1: Extend the mapping**

Change the signature to add `String userId`, and map `userId`, `userNoteIndexGsiPk`, and `userNoteIndexGsiSk`:

```java
@Mapping(target = "id", source = "noteId")
@Mapping(target = "front", source = "noteSchema.front")
@Mapping(target = "back", source = "noteSchema.back")
@Mapping(
    target = "lastFormattedAt",
    source = "noteSchema",
    qualifiedByName = "emptyLastFormattedAt")
@Mapping(target = "content", ignore = true)
@Mapping(target = "pk", source = "deckId", qualifiedByName = "notePk")
@Mapping(target = "sk", source = "noteId", qualifiedByName = "noteSk")
@Mapping(target = "userId", source = "userId")
@Mapping(target = "userNoteIndexGsiPk", source = "userId", qualifiedByName = "userNoteIndexGsiPk")
@Mapping(target = "userNoteIndexGsiSk", source = "noteId", qualifiedByName = "userNoteIndexGsiSk")
NoteEntity toNoteEntity(UUID deckId, UUID noteId, NoteSchema noteSchema, String userId);
```

Add the two named helpers next to the existing `toNotePk`/`toNoteSk`:

```java
@Named("userNoteIndexGsiPk")
default String toUserNoteIndexGsiPk(String userId) {
  return NoteKeys.userNoteIndexGsiPk(userId);
}

@Named("userNoteIndexGsiSk")
default String toUserNoteIndexGsiSk(UUID noteId) {
  return NoteKeys.noteSk(noteId);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/mapping/NoteEntityMapper.java
git commit -m "feat: add userId to NoteEntityMapper mapping"
```

---

### Task 4: Update mapper call sites

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java`

**Interfaces:**
- Consumes: new `toNoteEntity(deckId, noteId, noteSchema, userId)` signature (Task 3).
- Produces: nothing new downstream; keeps compilation valid.

- [ ] **Step 1: Update `DeckService`**

In `handleDerivedNotes` (`DeckService.java:114`), pass `"default"`:

```java
noteEntityMapper.toNoteEntity(deckId, UUID.randomUUID(), new NoteSchema(d.getFront(), d.getBack()), "default")
```

In `handleUnmappedNotes` → `toNoteEntity` (`DeckService.java:144`), pass `"default"`:

```java
return noteEntityMapper.toNoteEntity(deckId, UUID.randomUUID(), schema, "default");
```

In `storeDraftNote` (`DeckService.java:252`), pass `"default"`:

```java
noteEntityMapper.toNoteEntity(deckId, UUID.randomUUID(), new NoteSchema(draft.getFront(), draft.getBack()), "default")
```

- [ ] **Step 2: Update `NoteService.chat`**

In `NoteService.chat` tool handler (`NoteService.java:83`), pass `"default"`:

```java
noteEntityMapper.toNoteEntity(deckId, noteId, new NoteSchema(m.front(), m.back()), "default")
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java
git commit -m "feat: pass userId to note entity mapping at call sites"
```

---

### Task 5: Register `userNoteIndex` bean and add repository query

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckRepository.java`

**Interfaces:**
- Consumes: `NoteKeys.userNoteIndexGsiPk(String userId)` (Task 2).
- Produces: `@Bean DynamoDbIndex<NoteEntity> userNoteIndex(DynamoDbTable<NoteEntity> noteTable)`; `List<NoteEntity> findNotesByUserId(String userId)` — consumed by `DeckService.getAllNotes()` (Task 6).

- [ ] **Step 1: Add the index bean in `DynamoDbClientConfig`**

Add next to the other index beans (after `userDeckIndex`):

```java
@Bean
DynamoDbIndex<NoteEntity> userNoteIndex(DynamoDbTable<NoteEntity> noteTable) {
  return noteTable.index("UserNoteIndex");
}
```

- [ ] **Step 2: Add `findNotesByUserId` in `DeckRepository`**

Add a field and method:

```java
private final DynamoDbIndex<NoteEntity> userNoteIndex;

public List<NoteEntity> findNotesByUserId(String userId) {
  var condition =
      QueryConditional.keyEqualTo(
          Key.builder().partitionValue(NoteKeys.userNoteIndexGsiPk(userId)).build());

  return userNoteIndex
      .query(QueryEnhancedRequest.builder().queryConditional(condition).build())
      .items()
      .stream()
      .toList();
}
```

`NoteKeys` is already imported in `DeckRepository`; `QueryConditional`, `Key`, and `QueryEnhancedRequest` are already imported via the wildcard imports.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckRepository.java
git commit -m "feat: add UserNoteIndex bean and findNotesByUserId query"
```

---

### Task 6: Expose `DeckService.getAllNotes()`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`

**Interfaces:**
- Consumes: `DeckRepository.findNotesByUserId(String userId)` (Task 5).
- Produces: `List<NoteEntity> getAllNotes()` — the public API for fetching all notes of the current user.

- [ ] **Step 1: Add the method**

Place near `getNotes(UUID deckId)`:

```java
public List<NoteEntity> getAllNotes() {
  return deckRepository.findNotesByUserId("default");
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: add DeckService.getAllNotes via UserNoteIndex"
```
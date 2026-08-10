# Add `lastFormattedAt` to Derived Notes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track when a derived note was last formatted by adding an internal `Optional<Instant> lastFormattedAt` to `DerivedNoteEntity`, stamped on explicit format operations only.

**Architecture:** `DerivedNoteEntity` gains an `Optional<Instant> lastFormattedAt` attribute persisted via a new `OptionalInstantConverter` (same pattern as `OptionalStringConverter`/`AbstractChatMessageEntity`). `AnkiNoteService.formatNote` and `BulkFormatService.processNotes` stamp `Optional.of(Instant.now())` after formatting. Chat-created notes and legacy records keep `Optional.empty()`. No DTO/REST changes.

**Tech Stack:** Spring Boot 4.0.3, AWS SDK v2 DynamoDB Enhanced Client, Lombok, MapStruct.

## Global Constraints

- **JDK:** build with `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH` (system `java` is JDK 21; `pom.xml` requires release 25).
- **Compile command:** `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
- **`main` is pre-existing-broken:** current `main` has unrelated compile errors (e.g. `DeckCron`, `AnalysisService`, `AnalysisController`, `BulkFormatService`). Do NOT fix them. Each task's compile step is run to check the touched files add **no NEW errors**; treat an exit ≠ 0 listing only pre-existing errors as acceptable.
- **No API changes:** `DerivedNoteResponse`, `AnkiNoteMapper`, `AnalysisController`, and all REST endpoints stay unchanged. `lastFormattedAt` is internal only.
- **Set only on format operations:** `AnkiNoteService.formatNote` (both existing-note update and new-note creation) and `BulkFormatService.processNotes`. `AnkiNoteService.chat(...)` is untouched — chat-created notes keep `Optional.empty()`.
- **Storage pattern:** `Optional<Instant>` field with `@Getter(onMethod_ = @DynamoDbConvertedBy(OptionalInstantConverter.class))`, initialized `= Optional.empty()` so legacy records (attribute missing) deserialize as empty. No data migration.
- **Commit style:** one small, focused commit per task, matching repo convention.
- Branch: current checked-out branch (`main`) unless an isolated worktree is created at execution time.

---

### Task 1: Create `OptionalInstantConverter`

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/OptionalInstantConverter.java`

**Interfaces:**
- Consumes: nothing.
- Produces (used by Task 2 via `@DynamoDbConvertedBy(OptionalInstantConverter.class)`):
  - `OptionalInstantConverter implements AttributeConverter<Optional<Instant>>` with `transformFrom(Optional<Instant>) → AttributeValue`, `transformTo(AttributeValue) → Optional<Instant>`, `type() → EnhancedType.optionalOf(Instant.class)`, `attributeValueType() → AttributeValueType.S`.

- [ ] **Step 1: Create the converter**

```java
package com.felixkroemer.smort.infrastructure.dynamodb;

import java.time.Instant;
import java.util.Optional;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class OptionalInstantConverter implements AttributeConverter<Optional<Instant>> {

  @Override
  public AttributeValue transformFrom(Optional<Instant> input) {
    return input
        .map(instant -> AttributeValue.builder().s(instant.toString()).build())
        .orElse(AttributeValue.builder().nul(true).build());
  }

  @Override
  public Optional<Instant> transformTo(AttributeValue input) {
    if (input.nul() != null && input.nul()) return Optional.empty();
    return Optional.ofNullable(input.s()).map(Instant::parse);
  }

  @Override
  public EnhancedType<Optional<Instant>> type() {
    return EnhancedType.optionalOf(Instant.class);
  }

  @Override
  public AttributeValueType attributeValueType() {
    return AttributeValueType.S;
  }
}
```

Mirrors `OptionalStringConverter` exactly: present value stored as ISO-8601 string (`S`), empty stored as a NULL attribute.

- [ ] **Step 2: Compile (check for new errors only)**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: any errors shown are the pre-existing `main` ones; no new errors referencing `OptionalInstantConverter`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/OptionalInstantConverter.java
git commit -m "add OptionalInstantConverter for derived note timestamps"
```

---

### Task 2: Add `lastFormattedAt` field to `DerivedNoteEntity`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/DerivedNoteEntity.java`

**Interfaces:**
- Consumes: `OptionalInstantConverter` (Task 1).
- Produces (used by Task 3 and Task 4): `DerivedNoteEntity.getLastFormattedAt()` / `setLastFormattedAt(Optional<Instant>)`, defaulting to `Optional.empty()`.

- [ ] **Step 1: Add the field and imports**

Add imports:

```java
import com.felixkroemer.smort.infrastructure.dynamodb.OptionalInstantConverter;
import java.time.Instant;
import java.util.Optional;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
```

Add the field after `back` (with the same getter-annotation style as `AbstractChatMessageEntity`):

```java
  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalInstantConverter.class))
  private Optional<Instant> lastFormattedAt = Optional.empty();
```

The `DerivedNoteEntity(UUID analysisId, Long noteId, String front, String back)` constructor is unchanged — it does not set `lastFormattedAt`, so chat-created notes stay `Optional.empty()`.

- [ ] **Step 2: Compile (check for new errors only)**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: any errors shown are the pre-existing `main` ones; no new errors referencing `DerivedNoteEntity`/`OptionalInstantConverter`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/DerivedNoteEntity.java
git commit -m "add lastFormattedAt to DerivedNoteEntity"
```

---

### Task 3: Stamp `lastFormattedAt` in `AnkiNoteService.formatNote`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java`

**Interfaces:**
- Consumes: `DerivedNoteEntity.setLastFormattedAt(Optional<Instant>)` (Task 2).
- Produces: `formatNote(UUID, Long) → DerivedNoteEntity` now returns a derived note whose `lastFormattedAt` is `Optional.of(Instant.now())`; `chat(...)` unchanged.

- [ ] **Step 1: Add the `java.time.Instant` import**

Add next to the existing imports:

```java
import java.time.Instant;
```

(`java.util.Optional` is already imported.)

- [ ] **Step 2: Stamp the timestamp in both `formatNote` paths**

Replace the `derivedNote` construction block (lines ~59-71):

```java
    var derivedNote =
        getDerivedNote(analysisId, noteId)
            .map(
                d -> {
                  d.setFront(noteSchema.getFront());
                  d.setBack(noteSchema.getBack());
                  d.setLastFormattedAt(Optional.of(Instant.now()));
                  return d;
                })
            .orElseGet(
                () -> {
                  var newNote =
                      new DerivedNoteEntity(
                          analysisId, noteId, noteSchema.getFront(), noteSchema.getBack());
                  newNote.setLastFormattedAt(Optional.of(Instant.now()));
                  return newNote;
                });
```

Both the existing-note update and the new-note creation stamp `Optional.of(Instant.now())`.

- [ ] **Step 3: Compile (check for new errors only)**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: any errors shown are the pre-existing `main` ones; no new errors referencing `formatNote`/`lastFormattedAt`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java
git commit -m "stamp lastFormattedAt when formatting a note"
```

---

### Task 4: Stamp `lastFormattedAt` in `BulkFormatService.processNotes`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java`

**Interfaces:**
- Consumes: `DerivedNoteEntity.setLastFormattedAt(Optional<Instant>)` (Task 2).
- Produces: every `DerivedNoteEntity` created during bulk formatting carries `lastFormattedAt = Optional.of(Instant.now())`.

- [ ] **Step 1: Add the `java.util.Optional` import**

Add next to the existing imports:

```java
import java.util.Optional;
```

(`java.time.Instant` is already imported in this file.)

- [ ] **Step 2: Stamp the timestamp on the bulk-created note**

Replace the derived-note creation in `processNotes` (lines ~104-107):

```java
        var noteSchema = chatService.formatNote(content);
        var derivedNote =
            new DerivedNoteEntity(
                analysisId, noteEntity.getId(), noteSchema.getFront(), noteSchema.getBack());
        derivedNote.setLastFormattedAt(Optional.of(Instant.now()));
        derivedNoteRepository.save(derivedNote);
```

- [ ] **Step 3: Compile (check for new errors only)**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: any errors shown are the pre-existing `main` ones; no new errors referencing `processNotes`/`lastFormattedAt`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java
git commit -m "stamp lastFormattedAt on bulk formatted notes"
```

---

## Self-Review Notes

- **Spec coverage:** `OptionalInstantConverter` (Task 1), entity field (Task 2), `formatNote` stamp — existing update + new creation (Task 3), `processNotes` stamp (Task 4). Chat untouched; DTO/API unchanged; no migration — all per spec.
- **Type consistency:** `OptionalInstantConverter`, `Optional<Instant> lastFormattedAt`, `getLastFormattedAt()`/`setLastFormattedAt(Optional<Instant>)` are the same names/types across Tasks 1-4.
- **Verification caveat:** `main` is pre-existing-broken; compile steps check for NEW errors only, consistent with the user's instruction not to worry about the current compile state.

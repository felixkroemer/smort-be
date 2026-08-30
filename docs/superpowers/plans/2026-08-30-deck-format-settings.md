# Deck Format Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give decks the same `formatInstructions` settings capability analyses already have (storage, GET/PATCH API, and wiring into the deck formatting flows), kept symmetrical to the analysis settings feature.

**Architecture:** Mirror the existing analysis settings pattern for decks. `formatInstructions` is stored as an `Optional<String>` on `DeckMetaEntity` (persisted via `OptionalStringConverter`), exposed on the `Deck` domain object and a new `DeckSettings` record, read/updated through `DeckService`, and consumed by `NoteService.formatNote` and `DeckBulkFormatService` instead of the hardcoded `Optional.empty()`.

**Tech Stack:** Java, Spring Boot (REST controllers, MapStruct mappers, Lombok), DynamoDB Enhanced Client (DynamoDbBean entities).

## Global Constraints

- Keep all changes symmetric with the existing analysis settings feature (see `AnalysisService.getAnalysisSettings` / `updateAnalysisSettings`, `AnalysisMetaEntity.formatInstructions`, `AnalysisSettings`, `AnalysisSettingsResponse`, `UpdateAnalysisSettingsRequest`, `AnalysisController` settings endpoints).
- Do NOT write tests (AGENTS.md: write tests only when explicitly asked).
- Do NOT run or fix the build (`./mvnw compile`, `./mvnw test`, etc.); compilation is verified later by the human. Note in the final report that compilation was skipped.
- All work happens on the feature branch `feat/deck-format-settings`.
- Commit message style: lowercase conventional prefixes (`feat:`, `docs:`, `refactor:`), matching the repo history.
- No emojis; no code comments unless the surrounding code has them.

---

### Task 1: Domain — `formatInstructions` on deck entity and domain object

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckMetaEntity.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/Deck.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/deck/DeckSettings.java`

**Interfaces:**
- Consumes: nothing (mirrors `AnalysisMetaEntity` at `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaEntity.java:34-35`).
- Produces: `DeckMetaEntity.getFormatInstructions()` / `setFormatInstructions(Optional<String>)`, `Deck.getFormatInstructions()`, `DeckSettings(Optional<String> formatInstructions)` record. These are consumed by Task 2.

- [ ] **Step 1: Add `formatInstructions` to `DeckMetaEntity`**

Add the import `java.util.Optional` and this field (mirroring `AnalysisMetaEntity`), after the `status` field:

```java
  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  private Optional<String> formatInstructions = Optional.empty();
```

`OptionalStringConverter` is already imported by this file. The resulting file's relevant section:

```java
import java.util.Optional;
import java.util.UUID;
...
  private DeckStatus status;

  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  private Optional<String> formatInstructions = Optional.empty();
```

- [ ] **Step 2: Add `formatInstructions` to the `Deck` domain object**

Add this field (after `draftNote`), matching the `Analysis` domain object:

```java
  private Optional<String> formatInstructions = Optional.empty();
```

`java.util.Optional` is already imported in `Deck.java`. MapStruct's `DeckEntityMapper.toDeck(DeckMetaEntity, Optional<BulkFormat>, Optional<DraftNote>)` will map `meta.formatInstructions` → `deck.formatInstructions` automatically; no mapper change is needed.

- [ ] **Step 3: Create the `DeckSettings` record**

Create `src/main/java/com/felixkroemer/smort/domain/deck/DeckSettings.java`:

```java
package com.felixkroemer.smort.domain.deck;

import java.util.Optional;

public record DeckSettings(Optional<String> formatInstructions) {}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckMetaEntity.java src/main/java/com/felixkroemer/smort/domain/deck/Deck.java src/main/java/com/felixkroemer/smort/domain/deck/DeckSettings.java
git commit -m "feat: add formatInstructions to deck domain and entity"
```

---

### Task 2: Service — settings read/update in `DeckService`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`

**Interfaces:**
- Consumes: `DeckMetaEntity.getFormatInstructions()` / `setFormatInstructions(Optional<String>)` and the `DeckSettings` record from Task 1; existing private `getMeta(UUID)`.
- Produces: `DeckSettings getDeckSettings(UUID deckId)` and `DeckSettings updateDeckSettings(UUID deckId, Optional<String> formatInstructions)`. Consumed by Tasks 3 and 4.

- [ ] **Step 1: Add the settings methods**

Add these two public methods to `DeckService` (next to the other public methods, e.g. after `deleteDeck`), mirroring `AnalysisService.getAnalysisSettings` / `updateAnalysisSettings`:

```java
  public DeckSettings getDeckSettings(UUID deckId) {
    return new DeckSettings(getMeta(deckId).getFormatInstructions());
  }

  public DeckSettings updateDeckSettings(UUID deckId, Optional<String> formatInstructions) {
    var deck = getMeta(deckId);
    if (formatInstructions != null) {
      deck.setFormatInstructions(formatInstructions);
      deckRepository.saveDeckMeta(deck);
    }
    return new DeckSettings(deck.getFormatInstructions());
  }
```

`DeckSettings`, `Optional`, and `UUID` are all already in scope (same package / existing imports). Note: unlike the analysis version, there is no `updatedAt` write because `DeckMetaEntity` does not track timestamps — this is the intended deviation from the spec.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: add settings read and update to DeckService"
```

---

### Task 3: API — settings DTOs, mapper, controller endpoints

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/deck/dto/DeckSettingsResponse.java`
- Create: `src/main/java/com/felixkroemer/smort/application/deck/dto/UpdateDeckSettingsRequest.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/mapping/DeckRestMapper.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`

**Interfaces:**
- Consumes: `DeckService.getDeckSettings(UUID)` / `updateDeckSettings(UUID, Optional<String>)` from Task 2.
- Produces: REST endpoints `GET /decks/{deckId}/settings` and `PATCH /decks/{deckId}/settings` returning `DeckSettingsResponse`. `DeckSettingsResponse.formatInstructions()` is consumed by external clients.

- [ ] **Step 1: Create the response DTO**

Create `src/main/java/com/felixkroemer/smort/application/deck/dto/DeckSettingsResponse.java`:

```java
package com.felixkroemer.smort.application.deck.dto;

import java.util.Optional;

public record DeckSettingsResponse(Optional<String> formatInstructions) {}
```

- [ ] **Step 2: Create the request DTO**

Create `src/main/java/com/felixkroemer/smort/application/deck/dto/UpdateDeckSettingsRequest.java`:

```java
package com.felixkroemer.smort.application.deck.dto;

import java.util.Optional;

public record UpdateDeckSettingsRequest(Optional<String> formatInstructions) {}
```

- [ ] **Step 3: Add the mapper method**

In `src/main/java/com/felixkroemer/smort/application/deck/mapping/DeckRestMapper.java`, add imports for `DeckSettingsResponse` and `DeckSettings`, and this method:

```java
  DeckSettingsResponse toDeckSettingsResponse(DeckSettings settings);
```

- [ ] **Step 4: Add the controller endpoints**

In `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`, add imports for `com.felixkroemer.smort.application.deck.dto.DeckSettingsResponse` and `com.felixkroemer.smort.application.deck.dto.UpdateDeckSettingsRequest`, then add these two endpoints (e.g. after `getDecks`):

```java
  @GetMapping("/{deckId}/settings")
  public DeckSettingsResponse getDeckSettings(@PathVariable("deckId") UUID deckId) {
    return deckRestMapper.toDeckSettingsResponse(deckService.getDeckSettings(deckId));
  }

  @PatchMapping("/{deckId}/settings")
  public DeckSettingsResponse updateDeckSettings(
      @PathVariable("deckId") UUID deckId,
      @RequestBody UpdateDeckSettingsRequest updateDeckSettingsRequest) {
    return deckRestMapper.toDeckSettingsResponse(
        deckService.updateDeckSettings(deckId, updateDeckSettingsRequest.formatInstructions()));
  }
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/dto/DeckSettingsResponse.java src/main/java/com/felixkroemer/smort/application/deck/dto/UpdateDeckSettingsRequest.java src/main/java/com/felixkroemer/smort/application/deck/mapping/DeckRestMapper.java src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add deck settings endpoints"
```

---

### Task 4: Formatting — wire deck settings into format flows

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java`

**Interfaces:**
- Consumes: `DeckService.getDeckSettings(UUID)` from Task 2 (returns `DeckSettings` with `formatInstructions()`).
- Produces: nothing new; both deck formatting flows now pass the deck's `formatInstructions` to `ChatOrchestrationService.formatNote` instead of `Optional.empty()`.

- [ ] **Step 1: Wire `NoteService.formatNote`**

In `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java`:

1. Add the `DeckService` dependency to the `@RequiredArgsConstructor` field list (same package, no import needed):

```java
  private final DeckService deckService;
```

2. In `formatNote`, replace the `Optional.empty()` argument to `chatOrchestrationService.formatNote` with the deck's settings. Change:

```java
    var chatMessages =
        chatOrchestrationService.formatNote(
            DeckKeys.deckPk(deckId),
            noteId,
            note.getFront(),
            note.getBack(),
            Optional.empty(),
            toolHandlers);
```

to:

```java
    var formatInstructions = deckService.getDeckSettings(deckId).formatInstructions();
    var chatMessages =
        chatOrchestrationService.formatNote(
            DeckKeys.deckPk(deckId),
            noteId,
            note.getFront(),
            note.getBack(),
            formatInstructions,
            toolHandlers);
```

- [ ] **Step 2: Wire `DeckBulkFormatService.processNotes`**

In `src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java`:

1. Add the `DeckService` dependency to the field list (same package, no import needed):

```java
  private final DeckService deckService;
```

2. In `processNotes(DeckBulkFormatEntity job, List<NoteEntity> notesToProcess)`, fetch the settings once before `bulkFormatEngine.process(...)`:

```java
    var formatInstructions = deckService.getDeckSettings(job.getDeckId()).formatInstructions();
```

3. Inside the note lambda, replace `Optional.empty()` with `formatInstructions` in the `chatOrchestrationService.formatNote` call:

```java
          chatOrchestrationService.formatNote(
              DeckKeys.deckPk(job.getDeckId()),
              note.getId(),
              note.getFront(),
              note.getBack(),
              formatInstructions,
              toolHandlers);
```

The `Optional` import stays: `Optional.of(Instant.now())` is still used in the tool handler.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java
git commit -m "feat: use deck format settings in formatting flows"
```

---

### Task 5: Final report

- [ ] **Step 1: Report completion**

Summarize what was implemented, note the deviation (no `updatedAt` write on deck settings update) and that compilation was skipped per AGENTS.md. Confirm all commits are on `feat/deck-format-settings` and push the branch to origin:

```bash
git push -u origin feat/deck-format-settings
```

Do NOT merge into main; leave that to the human.
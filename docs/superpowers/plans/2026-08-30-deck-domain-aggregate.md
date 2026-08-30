# Deck Domain Aggregate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a domain `Deck` aggregate (mirroring `Analysis`) that combines the deck meta entity, the deck's bulk format, and the deck's draft note, and stop leaking `DeckMetaEntity` out of `DeckService`'s public API.

**Architecture:** `DeckService` currently returns the infrastructure entity `DeckMetaEntity` directly. We add a domain `Deck` POJO (fields from `DeckMetaEntity` + `Optional<BulkFormat>` + `Optional<DraftNote>`), a domain `DraftNote` record, and two MapStruct mappers (`DraftNoteEntityMapper` mirroring `BulkFormatEntityMapper`, `DeckEntityMapper` mirroring `AnalysisEntityMapper`). `DeckService` is reworked to mirror `AnalysisService`: public methods return domain types, a private `getMeta(UUID)` fetches the meta entity, and `getDecks()` aggregates bulk format + draft note per deck. `DeckRestMapper` and `DeckController` switch to the domain class.

**Tech Stack:** Java 21, Spring, MapStruct (`componentModel = "spring"`, `unmappedTargetPolicy = ReportingPolicy.ERROR`), Lombok, DynamoDB.

## Global Constraints

- **No tests.** Project convention (AGENTS.md): tests are only written when explicitly requested. This plan contains no test files.
- **No build verification.** Per AGENTS.md, implementing subagents must NOT run/fix/debug the build (`./mvnw compile`, `./mvnw test`). The human owns compilation. Do not run build commands; note "compilation skipped per AGENTS.md" in commit messages and reports.
- **Branch:** Work on `feature/deck-domain-aggregate` only. Never commit to main.
- **MapStruct style:** Every mapper interface uses `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)`.
- **No `DeckMetaEntity` in public API:** `DeckService` public methods must not return or accept `DeckMetaEntity` (private/internal use is fine).
- Existing code style: 2-space indent, no comments.

---

### Task 1: Create domain `DraftNote` record and `DraftNoteEntityMapper`

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/deck/DraftNote.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/deck/mapping/DraftNoteEntityMapper.java`

**Interfaces:**
- Consumes: nothing from this plan (mirrors `BulkFormatEntityMapper` at `domain/common/mapping/BulkFormatEntityMapper.java`).
- Produces: `com.felixkroemer.smort.domain.deck.DraftNote` (record with `String front()`, `String back()`) and `DraftNoteEntityMapper.toDraftNote(DraftNoteEntity)` returning `DraftNote`. Used by Task 3.

- [ ] **Step 1: Create `DraftNote.java`**

```java
package com.felixkroemer.smort.domain.deck;

public record DraftNote(String front, String back) {}
```

- [ ] **Step 2: Create `DraftNoteEntityMapper.java`**

```java
package com.felixkroemer.smort.domain.deck.mapping;

import com.felixkroemer.smort.domain.deck.DraftNote;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DraftNoteEntityMapper {

  DraftNote toDraftNote(DraftNoteEntity entity);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DraftNote.java src/main/java/com/felixkroemer/smort/domain/deck/mapping/DraftNoteEntityMapper.java
git commit -m "feat: add domain DraftNote and DraftNoteEntityMapper"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 2: Create domain `Deck` class and `DeckEntityMapper`

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/deck/Deck.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/deck/mapping/DeckEntityMapper.java`

**Interfaces:**
- Consumes: `DeckMetaEntity` (`infrastructure/dynamodb/deck/DeckMetaEntity.java`; fields `deckId`, `name`, `userId`, `status`), `BulkFormat` (`domain/common/BulkFormat.java`), `DraftNote` from Task 1. Mirrors `AnalysisEntityMapper` at `domain/anki/mapping/AnalysisEntityMapper.java`.
- Produces: `com.felixkroemer.smort.domain.deck.Deck` (Lombok POJO with getters/setters for all fields) and `DeckEntityMapper.toDeck(DeckMetaEntity, Optional<BulkFormat>, Optional<DraftNote>)` returning `Deck`. Used by Tasks 3 and 4.

- [ ] **Step 1: Create `Deck.java`**

```java
package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Deck {
  private UUID deckId;
  private String name;
  private String userId;
  private DeckStatus status;
  private Optional<BulkFormat> bulkFormat = Optional.empty();
  private Optional<DraftNote> draftNote = Optional.empty();
}
```

- [ ] **Step 2: Create `DeckEntityMapper.java`**

```java
package com.felixkroemer.smort.domain.deck.mapping;

import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.domain.deck.Deck;
import com.felixkroemer.smort.domain.deck.DraftNote;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DeckEntityMapper {

  Deck toDeck(DeckMetaEntity meta, Optional<BulkFormat> bulkFormat, Optional<DraftNote> draftNote);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/Deck.java src/main/java/com/felixkroemer/smort/domain/deck/mapping/DeckEntityMapper.java
git commit -m "feat: add domain Deck and DeckEntityMapper"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 3: Rework `DeckService` to return domain `Deck`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`

**Interfaces:**
- Consumes: `Deck`, `DraftNote`, `DeckEntityMapper`, `DraftNoteEntityMapper` (Tasks 1-2); `BulkFormatRepository.findBulkFormatByDeckId(UUID)` (`infrastructure/dynamodb/BulkFormatRepository.java`); `BulkFormatEntityMapper.toBulkFormat(BulkFormatEntity)` (`domain/common/mapping/BulkFormatEntityMapper.java`); `DraftNoteRepository.findDraftNote(UUID)`.
- Produces: `DeckService.importDeck(UUID, Map<String, NoteTypeTemplate>)` returns `Deck`; `DeckService.getDecks()` returns `List<Deck>`; private `DeckMetaEntity getMeta(UUID)`. Consumed by Task 4.

- [ ] **Step 1: Add imports**

Add these imports, placed alphabetically with the existing ones (and `java.util.Optional` with the other `java.util` imports):

```java
import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.domain.deck.mapping.DeckEntityMapper;
import com.felixkroemer.smort.domain.deck.mapping.DraftNoteEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import java.util.Optional;
```

- [ ] **Step 2: Add fields**

After `private final NoteEntityMapper noteEntityMapper;`, add:

```java
  private final BulkFormatRepository bulkFormatRepository;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;
  private final DeckEntityMapper deckEntityMapper;
  private final DraftNoteEntityMapper draftNoteEntityMapper;
```

- [ ] **Step 3: Change `importDeck` return type and final mapping**

Change `public DeckMetaEntity importDeck(...)` to `public Deck importDeck(...)`. Replace the end of the method:

```java
    deck.setStatus(DeckStatus.ACTIVE);
    deckRepository.saveDeckMeta(deck);
    return deck;
```

with:

```java
    deck.setStatus(DeckStatus.ACTIVE);
    deckRepository.saveDeckMeta(deck);
    return deckEntityMapper.toDeck(deck, Optional.empty(), Optional.empty());
```

The rest of `importDeck` is unchanged (the local `deck` remains a `DeckMetaEntity` from `createDeck`).

- [ ] **Step 4: Replace `getDecks`**

Replace:

```java
  public List<DeckMetaEntity> getDecks() {
    return deckRepository.findDeckMetasByUserId("default");
  }
```

with:

```java
  public List<Deck> getDecks() {
    return deckRepository.findDeckMetasByUserId("default").stream()
        .map(
            entity ->
                deckEntityMapper.toDeck(
                    entity,
                    bulkFormatRepository
                        .findBulkFormatByDeckId(entity.getDeckId())
                        .map(bulkFormatEntityMapper::toBulkFormat),
                    draftNoteRepository
                        .findDraftNote(entity.getDeckId())
                        .map(draftNoteEntityMapper::toDraftNote)))
        .toList();
  }
```

- [ ] **Step 5: Add private `getMeta`**

Add at the end of the class, mirroring `AnalysisService.getMeta`:

```java
  private DeckMetaEntity getMeta(UUID deckId) {
    return deckRepository
        .findDeckMetaByDeckId(deckId)
        .orElseThrow(() -> new NotFoundException("Could not find deck by id. deckId={}", deckId));
  }
```

- [ ] **Step 6: Use `getMeta` in `deleteDeck`**

Replace:

```java
  public void deleteDeck(UUID deckId) {
    var deck =
        deckRepository
            .findDeckMetaByDeckId(deckId)
            .orElseThrow(() -> new NotFoundException("Could not find deck. deckId={}", deckId));
    deck.setStatus(DeckStatus.MARKED_FOR_DELETION);
    deckRepository.saveDeckMeta(deck);
  }
```

with:

```java
  public void deleteDeck(UUID deckId) {
    var deck = getMeta(deckId);
    deck.setStatus(DeckStatus.MARKED_FOR_DELETION);
    deckRepository.saveDeckMeta(deck);
  }
```

- [ ] **Step 7: Use `getMeta` in `chat`**

Replace:

```java
    var deck =
        deckRepository
            .findDeckMetaByDeckId(deckId)
            .orElseThrow(() -> new NotFoundException("Could not find deck. deckId={}", deckId));
```

with:

```java
    var deck = getMeta(deckId);
```

- [ ] **Step 8: Verify no `DeckMetaEntity` leaves the service**

Grep for `DeckMetaEntity` in `DeckService.java`. It must appear only in: the import, `createDeck` (return type + `new DeckMetaEntity`), and `getMeta` (return type) — all private/internal.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: return domain Deck from DeckService"
```

Note in the report: compilation skipped per AGENTS.md.

---

### Task 4: Switch `DeckRestMapper` and `DeckController` to domain `Deck`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/mapping/DeckRestMapper.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`

**Interfaces:**
- Consumes: `Deck` (Task 2) and `DeckService.importDeck` / `DeckService.getDecks` return types (Task 3).
- Produces: `DeckRestMapper.toDeckResponse(Deck)` and `DeckRestMapper.toDeckResponse(List<Deck>)`, both returning `DeckResponse` (record `deckId`, `name`). `DeckController` compiles against the new service return types.

- [ ] **Step 1: Replace `DeckRestMapper.java` contents**

```java
package com.felixkroemer.smort.application.deck.mapping;

import com.felixkroemer.smort.application.deck.dto.DeckResponse;
import com.felixkroemer.smort.domain.deck.Deck;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DeckRestMapper {

  DeckResponse toDeckResponse(Deck deck);

  List<DeckResponse> toDeckResponse(List<Deck> decks);
}
```

- [ ] **Step 2: Update `DeckController.getDecks()`**

Replace:

```java
  @GetMapping
  public List<DeckResponse> getDecks() {
    var deckMetaEntities = deckService.getDecks();
    return deckRestMapper.toDeckResponse(deckMetaEntities);
  }
```

with:

```java
  @GetMapping
  public List<DeckResponse> getDecks() {
    var decks = deckService.getDecks();
    return deckRestMapper.toDeckResponse(decks);
  }
```

No other changes in `DeckController` (its `importAnalysis` already passes the result of `importDeck` straight into `deckRestMapper.toDeckResponse`, which now resolves to the `Deck` overload).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/mapping/DeckRestMapper.java src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: map domain Deck in REST layer"
```

Note in the report: compilation skipped per AGENTS.md.
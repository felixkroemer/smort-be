# All-Notes Endpoint with Stored deckId Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose every note of the current user across all decks via `GET /notes`, with each row carrying its stored `deckId` so a front-end table can join against `GET /decks`.

**Architecture:** `NoteEntity` gains a stored `deckId` attribute (set by `NoteEntityMapper.toNoteEntity`, which already receives `deckId`). A new `NoteController` at `notes` serves `deckService.getAllNotes()` through the existing `NoteRestMapper`, and `NoteResponse` picks up `deckId` automatically via MapStruct name matching (no mapper code change). The existing `UserNoteIndex` GSI already powers `getAllNotes()`.

**Tech Stack:** Java 17, Spring Web (`@RestController`, `@RequestMapping`), AWS SDK DynamoDB Enhanced Client (DynamoDbBean attribute), MapStruct, Lombok, Maven.

## Global Constraints

- Do not write tests (per AGENTS.md — tests only when explicitly requested).
- Do not run or debug the build (`./mvnw compile`, `./mvnw test`); compilation is owned by the human and skipped. Note this in the completion report.
- The current user is the hardcoded dummy `"default"` (no auth yet) — `getAllNotes()` already uses it.
- Notes written before this change lack the `deckId` attribute and map to `deckId: null`; this is accepted (human will wipe existing data). No backfill.
- Commit each task's changes to the feature branch `feat/all-notes-with-deck`.

---

### Task 1: Add `deckId` field to `NoteEntity`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/NoteEntity.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `NoteEntity` gains a plain `UUID deckId` field (no key/GSI annotation) — read and written by `NoteEntityMapper` in Task 2, read by `NoteRestMapper` (Task 3/4).

- [ ] **Step 1: Add the field**

Add `deckId` directly after the existing `id` field (`NoteEntity.java:30`):

```java
  private UUID id;
  private UUID deckId;
  private String front;
```

No other changes — `@DynamoDbBean`, `@Getter`, `@Setter`, and `UUID` (already imported) cover the new field.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/NoteEntity.java
git commit -m "feat: add deckId field to NoteEntity"
```

---

### Task 2: Stamp `deckId` in `NoteEntityMapper`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/mapping/NoteEntityMapper.java`

**Interfaces:**
- Consumes: `NoteEntity.deckId` field (Task 1).
- Produces: `toNoteEntity(UUID deckId, UUID noteId, NoteSchema noteSchema, String userId)` now populates `deckId` — every note write stores the attribute.

- [ ] **Step 1: Add the mapping**

Add one `@Mapping` line after the existing `id` mapping (`NoteEntityMapper.java:19-20`):

```java
  @Mapping(target = "id", source = "noteId")
  @Mapping(target = "deckId", source = "deckId")
  @Mapping(target = "front", source = "noteSchema.front")
```

`Mapping` is already imported (`NoteEntityMapper.java:12`) and `deckId` is already a method parameter (`NoteEntityMapper.java:32`).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/mapping/NoteEntityMapper.java
git commit -m "feat: map deckId in NoteEntityMapper"
```

---

### Task 3: Add `deckId` to `NoteResponse`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/dto/NoteResponse.java`

**Interfaces:**
- Consumes: `NoteEntity.deckId` — MapStruct in `NoteRestMapper` maps it to the response field by name (no mapper change needed; verified `NoteRestMapper` is the only producer of `NoteResponse`).
- Produces: `record NoteResponse(UUID id, UUID deckId, String front, String back, Instant lastFormattedAt)` — the payload returned by `DeckController.getNote`, `DeckController.getNotes`, and the new `NoteController` (Task 4).

- [ ] **Step 1: Extend the record**

```java
public record NoteResponse(UUID id, UUID deckId, String front, String back, Instant lastFormattedAt) {}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/dto/NoteResponse.java
git commit -m "feat: add deckId to NoteResponse"
```

---

### Task 4: Add `NoteController` with `GET /notes`

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/deck/NoteController.java`

**Interfaces:**
- Consumes: `DeckService.getAllNotes()` returning `List<NoteEntity>` (exists, `DeckService.java:73`), `NoteRestMapper.toNoteResponse(List<NoteEntity>)` (exists, maps with Task 1 + Task 3 in effect).
- Produces: `GET /notes` returning `List<NoteResponse>` — the endpoint the all-notes table consumes.

- [ ] **Step 1: Create the controller**

```java
package com.felixkroemer.smort.application.deck;

import com.felixkroemer.smort.application.deck.dto.NoteResponse;
import com.felixkroemer.smort.application.deck.mapping.NoteRestMapper;
import com.felixkroemer.smort.domain.deck.DeckService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("notes")
public class NoteController {

  private final DeckService deckService;
  private final NoteRestMapper noteRestMapper;

  @GetMapping
  public List<NoteResponse> getAllNotes() {
    return noteRestMapper.toNoteResponse(deckService.getAllNotes());
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/NoteController.java
git commit -m "feat: add GET /notes endpoint for all user notes"
```
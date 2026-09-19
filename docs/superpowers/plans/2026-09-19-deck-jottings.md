# Deck Jottings (Backlog) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-deck backlog of jottings (title + description) with CRUD REST endpoints under `/decks/{deckId}/jottings`.

**Architecture:** Each jotting is a DynamoDB item in the existing `common-table` under the deck partition, parallel to notes: `pk = DECK#<deckId>`, `sk = JOTTING#<jottingId>`. A `JottingService` provides CRUD, a `JottingRepository` persists, MapStruct `JottingRestMapper` maps entity → response, and endpoints live in `DeckController`. Jottings are deleted when a deck is deleted via `CleanupCron`. No GSI or terraform changes.

**Tech Stack:** Java 17, Spring Boot, AWS SDK DynamoDB Enhanced Client, Lombok, MapStruct.

## Global Constraints

- No tests are written (per AGENTS.md "write tests only when explicitly asked").
- Do NOT attempt to run, fix, or debug the build (`./mvnw compile`, `./mvnw test`, etc.). Compilation is owned by the human and verified later.
- Do not stage or commit `.idea/` or its contents.
- All work happens on the `feat/deck-jottings` branch in the `.worktrees/feat/deck-jottings` worktree.
- Use `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)` for all new MapStruct mappers.
- Split long argument lists onto one argument per line (line comment style used in this repo), e.g. `DeleteNotesRequest` / `UpdateDeckSettingsRequest`.

---

### Task 1: Sort keys and DynamoDB entity

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/JottingKeys.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/JottingEntity.java`

**Interfaces:**
- Consumes: `DeckKeys.deckPk(UUID)` (`infrastructure/dynamodb/keys/partition/DeckKeys.java`)
- Produces: `JottingKeys.jottingSk(UUID)` → `"JOTTING#<id>"`; `JottingKeys.jottingPrefix()` → `"JOTTING#"`; `JottingEntity` bean with fields `pk`, `sk`, `id`, `deckId`, `title`, `description` and constructor `JottingEntity(UUID deckId, UUID jottingId, String title, String description)`.

- [ ] **Step 1: Create `JottingKeys.java`**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.util.UUID;

public final class JottingKeys {

  public static String jottingSk(UUID jottingId) {
    return "JOTTING#" + jottingId;
  }

  public static String jottingPrefix() {
    return "JOTTING#";
  }
}
```

- [ ] **Step 2: Create `JottingEntity.java`**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.JottingKeys;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class JottingEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private UUID id;
  private UUID deckId;
  private String title;
  private String description;

  public JottingEntity(UUID deckId, UUID jottingId, String title, String description) {
    this.pk = DeckKeys.deckPk(deckId);
    this.sk = JottingKeys.jottingSk(jottingId);
    this.id = jottingId;
    this.deckId = deckId;
    this.title = title;
    this.description = description;
  }
}
```

- [ ] **Step 3: Verify no build, commit**

Compilation is skipped per AGENTS.md. Stage only the two new files.

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/JottingKeys.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/JottingEntity.java
git commit -m "feat: add jotting keys and entity"
```

---

### Task 2: Repository and table registration

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/JottingRepository.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java` (add a `jottingTable` bean and the `JottingEntity` import)

**Interfaces:**
- Consumes: `JottingEntity`, `JottingKeys`, `DeckKeys`
- Produces: `JottingRepository` with:
  - `void save(JottingEntity jotting)`
  - `List<JottingEntity> findJottingsByDeckId(UUID deckId)`
  - `Optional<JottingEntity> findJotting(UUID deckId, UUID jottingId)`
  - `void delete(UUID deckId, UUID jottingId)`
  - `void deleteAllJottings(UUID deckId)`

- [ ] **Step 1: Create `JottingRepository.java`**

Modeled on `DeckRepository.findNotesByDeckId`, `DeckRepository.deleteDeckNotes` (batch delete in chunks of 25).

```java
package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.JottingKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

@Repository
@RequiredArgsConstructor
@Slf4j
public class JottingRepository {

  private final DynamoDbTable<JottingEntity> jottingTable;
  private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

  public void save(JottingEntity jotting) {
    jottingTable.putItem(jotting);
  }

  public List<JottingEntity> findJottingsByDeckId(UUID deckId) {
    var condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(DeckKeys.deckPk(deckId))
                .sortValue(JottingKeys.jottingPrefix())
                .build());

    return jottingTable.query(condition).items().stream().toList();
  }

  public Optional<JottingEntity> findJotting(UUID deckId, UUID jottingId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(JottingKeys.jottingSk(jottingId))
            .build();

    return Optional.ofNullable(jottingTable.getItem(key));
  }

  public void delete(UUID deckId, UUID jottingId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(JottingKeys.jottingSk(jottingId))
            .build();
    jottingTable.deleteItem(key);
  }

  public void deleteAllJottings(UUID deckId) {
    var condition =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(DeckKeys.deckPk(deckId))
                .sortValue(JottingKeys.jottingPrefix())
                .build());

    List<JottingEntity> keys =
        jottingTable
            .query(
                QueryEnhancedRequest.builder()
                    .queryConditional(condition)
                    .attributesToProject("pk", "sk")
                    .build())
            .items()
            .stream()
            .toList();

    IntStream.range(0, (keys.size() + 24) / 25)
        .mapToObj(i -> keys.subList(i * 25, Math.min((i + 1) * 25, keys.size())))
        .forEach(
            batch -> {
              WriteBatch.Builder<JottingEntity> writeBatch =
                  WriteBatch.builder(JottingEntity.class).mappedTableResource(jottingTable);
              batch.forEach(
                  item ->
                      writeBatch.addDeleteItem(
                          Key.builder()
                              .partitionValue(item.getPk())
                              .sortValue(item.getSk())
                              .build()));
              dynamoDbEnhancedClient.batchWriteItem(
                  BatchWriteItemEnhancedRequest.builder()
                      .writeBatches(writeBatch.build())
                      .build());
            });

    log.info("Deleted deck jottings. deckId={}", deckId);
  }
}
```

- [ ] **Step 2: Register the table bean in `DynamoDbClientConfig.java`**

Add the import alongside the other deck entity imports:

```java
import com.felixkroemer.smort.infrastructure.dynamodb.deck.JottingEntity;
```

Add this bean next to the `draftNoteTable` bean:

```java
  @Bean
  public DynamoDbTable<JottingEntity> jottingTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(JottingEntity.class));
  }
```

- [ ] **Step 3: Verify no build, commit**

Compilation is skipped per AGENTS.md. Stage the two files.

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/JottingRepository.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java
git commit -m "feat: add jotting repository and table registration"
```

---

### Task 3: DTOs and REST mapper

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/deck/dto/JottingResponse.java`
- Create: `src/main/java/com/felixkroemer/smort/application/deck/dto/CreateJottingRequest.java`
- Create: `src/main/java/com/felixkroemer/smort/application/deck/dto/UpdateJottingRequest.java`
- Create: `src/main/java/com/felixkroemer/smort/application/deck/mapping/JottingRestMapper.java`

**Interfaces:**
- Consumes: `JottingEntity`
- Produces: `JottingResponse(UUID id, UUID deckId, String title, String description)`; `CreateJottingRequest(String title, String description)`; `UpdateJottingRequest(String title, String description)`; `JottingRestMapper.toJottingResponse(JottingEntity)` and `JottingRestMapper.toJottingResponse(List<JottingEntity>)`.

- [ ] **Step 1: Create the three DTOs**

`JottingResponse.java`:

```java
package com.felixkroemer.smort.application.deck.dto;

import java.util.UUID;

public record JottingResponse(UUID id, UUID deckId, String title, String description) {}
```

`CreateJottingRequest.java`:

```java
package com.felixkroemer.smort.application.deck.dto;

public record CreateJottingRequest(String title, String description) {}
```

`UpdateJottingRequest.java`:

```java
package com.felixkroemer.smort.application.deck.dto;

public record UpdateJottingRequest(String title, String description) {}
```

- [ ] **Step 2: Create `JottingRestMapper.java`**

```java
package com.felixkroemer.smort.application.deck.mapping;

import com.felixkroemer.smort.application.deck.dto.JottingResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.JottingEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface JottingRestMapper {

  JottingResponse toJottingResponse(JottingEntity jottingEntity);

  List<JottingResponse> toJottingResponse(List<JottingEntity> jottingEntities);
}
```

- [ ] **Step 3: Verify no build, commit**

Compilation is skipped per AGENTS.md. Stage the four new files.

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/dto/JottingResponse.java src/main/java/com/felixkroemer/smort/application/deck/dto/CreateJottingRequest.java src/main/java/com/felixkroemer/smort/application/deck/dto/UpdateJottingRequest.java src/main/java/com/felixkroemer/smort/application/deck/mapping/JottingRestMapper.java
git commit -m "feat: add jotting DTOs and rest mapper"
```

---

### Task 4: Domain service

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/deck/JottingService.java`

**Interfaces:**
- Consumes: `JottingRepository` (Task 2), `NotFoundException(pattern, Object... args)` (`common/exception/NotFoundException.java`)
- Produces: `JottingService` with:
  - `JottingEntity create(UUID deckId, String title, String description)`
  - `List<JottingEntity> getJottings(UUID deckId)`
  - `JottingEntity update(UUID deckId, UUID jottingId, String title, String description)`
  - `void delete(UUID deckId, UUID jottingId)`
  - `JottingEntity getJotting(UUID deckId, UUID jottingId)`

- [ ] **Step 1: Create `JottingService.java`**

New jotting ID is generated with `UUID.randomUUID()` (parallel to `DeckService` note creation). `update` only applies non-null fields. `getJotting` throws `NotFoundException` when missing and is reused by `update`.

```java
package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.JottingEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.JottingRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JottingService {

  private final JottingRepository jottingRepository;

  public JottingEntity create(UUID deckId, String title, String description) {
    var jotting = new JottingEntity(deckId, UUID.randomUUID(), title, description);
    jottingRepository.save(jotting);
    return jotting;
  }

  public List<JottingEntity> getJottings(UUID deckId) {
    return jottingRepository.findJottingsByDeckId(deckId);
  }

  public JottingEntity getJotting(UUID deckId, UUID jottingId) {
    return jottingRepository
        .findJotting(deckId, jottingId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Could not find jotting. deckId={}, jottingId={}", deckId, jottingId));
  }

  public JottingEntity update(UUID deckId, UUID jottingId, String title, String description) {
    var jotting = getJotting(deckId, jottingId);
    if (title != null) jotting.setTitle(title);
    if (description != null) jotting.setDescription(description);
    jottingRepository.save(jotting);
    return jotting;
  }

  public void delete(UUID deckId, UUID jottingId) {
    jottingRepository.delete(deckId, jottingId);
  }
}
```

- [ ] **Step 2: Verify no build, commit**

Compilation is skipped per AGENTS.md. Stage the new file.

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/JottingService.java
git commit -m "feat: add jotting service"
```

---

### Task 5: Controller endpoints

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`

**Interfaces:**
- Consumes: `JottingService` (Task 4), `JottingRestMapper` (Task 3), `JottingResponse` / `CreateJottingRequest` / `UpdateJottingRequest` (Task 3)
- Produces: four endpoints on `DeckController`:
  - `GET /decks/{deckId}/jottings` → `List<JottingResponse>`
  - `POST /decks/{deckId}/jottings` → 201 + `JottingResponse`
  - `PATCH /decks/{deckId}/jottings/{jottingId}` → `JottingResponse`
  - `DELETE /decks/{deckId}/jottings/{jottingId}` → 204

- [ ] **Step 1: Add imports, fields, and endpoints to `DeckController.java`**

Add these imports to the existing deck-related imports:

```java
import com.felixkroemer.smort.application.deck.dto.CreateJottingRequest;
import com.felixkroemer.smort.application.deck.dto.JottingResponse;
import com.felixkroemer.smort.application.deck.dto.UpdateJottingRequest;
import com.felixkroemer.smort.application.deck.mapping.JottingRestMapper;
import com.felixkroemer.smort.domain.deck.JottingService;
```

Add fields alongside the existing service/mapper fields:

```java
  private final JottingService jottingService;
  private final JottingRestMapper jottingRestMapper;
```

Add these methods, following the existing `draft-note` endpoint style:

```java
  @GetMapping("/{deckId}/jottings")
  public List<JottingResponse> getJottings(@PathVariable("deckId") UUID deckId) {
    return jottingRestMapper.toJottingResponse(jottingService.getJottings(deckId));
  }

  @PostMapping("/{deckId}/jottings")
  @ResponseStatus(HttpStatus.CREATED)
  public JottingResponse createJotting(
      @PathVariable("deckId") UUID deckId,
      @RequestBody CreateJottingRequest createJottingRequest) {
    return jottingRestMapper.toJottingResponse(
        jottingService.create(
            deckId,
            createJottingRequest.title(),
            createJottingRequest.description()));
  }

  @PatchMapping("/{deckId}/jottings/{jottingId}")
  public JottingResponse updateJotting(
      @PathVariable("deckId") UUID deckId,
      @PathVariable("jottingId") UUID jottingId,
      @RequestBody UpdateJottingRequest updateJottingRequest) {
    return jottingRestMapper.toJottingResponse(
        jottingService.update(
            deckId,
            jottingId,
            updateJottingRequest.title(),
            updateJottingRequest.description()));
  }

  @DeleteMapping("/{deckId}/jottings/{jottingId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteJotting(
      @PathVariable("deckId") UUID deckId, @PathVariable("jottingId") UUID jottingId) {
    jottingService.delete(deckId, jottingId);
  }
```

- [ ] **Step 2: Verify no build, commit**

Compilation is skipped per AGENTS.md. Stage the controller.

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add jotting endpoints to deck controller"
```

---

### Task 6: Deck deletion cleanup

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/cron/CleanupCron.java`

**Interfaces:**
- Consumes: `JottingRepository.deleteAllJottings(UUID)` (Task 2)
- Produces: jottings are removed when a deck marked for deletion is cleaned up.

- [ ] **Step 1: Wire `JottingRepository` into `CleanupCron`**

Add the import alongside the other deck repository imports:

```java
import com.felixkroemer.smort.infrastructure.dynamodb.deck.JottingRepository;
```

Add the field alongside the other repository fields:

```java
  private final JottingRepository jottingRepository;
```

Add the call inside the `deleteDecksMarkedForDeletion` loop, right after the `deckRepository.deleteDeckNotes(...)` line:

```java
        jottingRepository.deleteAllJottings(deck.getDeckId());
```

- [ ] **Step 2: Verify no build, commit**

Compilation is skipped per AGENTS.md. Stage the cron file.

```bash
git add src/main/java/com/felixkroemer/smort/domain/cron/CleanupCron.java
git commit -m "feat: delete deck jottings during cleanup"
```

---

## Self-Review Notes

- Spec coverage: keys/entity (Task 1), repository + table bean (Task 2), DTOs + mapper (Task 3), service (Task 4), endpoints (Task 5), CleanupCron wiring (Task 6). No GSI/terraform changes, no tests — consistent with the spec.
- No placeholder content; all code is fully written inline.
- Type consistency: `JottingKeys.jottingSk/jottingPrefix`, `JottingEntity` constructor `(UUID deckId, UUID jottingId, String title, String description)`, `JottingRepository` method names, and `JottingService` method signatures are identical across all tasks.
# Move Analysis Session State to DynamoDB — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the JPA/Postgres `AnalysisEntity` with a DynamoDB `AnalysisMetaEntity` (`ANALYSIS#<id>` / `META#`), keep `BulkFormatEntity` alongside it (`ANALYSIS#<id>` / `META#BULKFORMAT#`), and have the service layer consume a domain `Analysis` that embeds `Optional<BulkFormat>`.

**Architecture:** Single-table DynamoDB. The `ANALYSIS#<analysisId>` partition holds `META#` (AnalysisMetaEntity), `META#BULKFORMAT#` (BulkFormatEntity), and `NOTE#<noteId>` (DerivedNoteEntity). `AnalysisService` composes both meta entities into a domain `Analysis` + `BulkFormat`; repositories stay thin. The entire Postgres/JPA stack is removed.

**Tech Stack:** Spring Boot 4.0.3, AWS SDK v2 DynamoDB Enhanced Client, Lombok, MapStruct, Hibernate (sqlite only, via `EntityManagerFactoryCache`).

## Global Constraints

- **JDK:** build with `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH` (system `java` is JDK 21; `pom.xml` requires release 25).
- **Verification:** `./mvnw compile` (with the JDK above). The context-load test is pre-existing-broken in this env and is NOT a gate.
- **Verification command:** `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q` — expected: exit 0, no output.
- **No data migration** — old Postgres rows and `BULKFORMAT#` items are discarded (start fresh).
- **No REST API changes** — `AnalysisResponse`, endpoints, and `BulkFormatStatusResponse` stay as-is.
- **Entity → domain:** `AnalysisMetaEntity.dbPath` is a `String` (the DynamoDB enhanced client cannot map `java.nio.file.Path`); the domain `Analysis.dbPath` stays `Path`. Conversion happens in `AnalysisService`.
- **Commit style:** follow repo convention — one small, focused commit per task.
- Branch: work on `ddb-only-analysis-meta`.

---

### Task 1: Change BulkFormat sort key to `META#BULKFORMAT#`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/BulkFormatKeys.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `BulkFormatKeys.bulkFormatSk()` and `BulkFormatKeys.bulkFormatPrefix()` now return `"META#BULKFORMAT#"`. All existing callers (`BulkFormatEntity`, `BulkFormatRepository`) use these methods and need no change.

- [ ] **Step 1: Update the sort key constants**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

public final class BulkFormatKeys {

  public static String bulkFormatSk() {
    return "META#BULKFORMAT#";
  }

  public static String bulkFormatPrefix() {
    return "META#BULKFORMAT#";
  }

}
```

- [ ] **Step 2: Compile**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: exit 0, no output.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/BulkFormatKeys.java
git commit -m "change bulk format sort key to META#BULKFORMAT#"
```

---

### Task 2: Add `AnalysisMetaEntity` + repository + config bean

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisStatus.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaEntity.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaRepository.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java`

**Interfaces:**
- Consumes: `AnalysisKeys.analysisPk(UUID)`, `MetaKeys.metaSk()`, `DynamoDbEnhancedClient`.
- Produces (used by Task 3 and Task 4):
  - `AnalysisStatus` enum in `infrastructure.dynamodb.anki`: `NEW`, `DB_UPLOADED`, `DECK_SELECTED`, `MARKED_FOR_DELETION`.
  - `AnalysisMetaEntity` — `@DynamoDbBean` with `pk`, `sk`, `dbPath` (String), `deckId` (Long), `deckName` (String), `status`, `createdAt`, `updatedAt`; constructor `AnalysisMetaEntity(UUID analysisId, AnalysisStatus status)`; `UUID getAnalysisId()`.
  - `AnalysisMetaRepository` — `Optional<AnalysisMetaEntity> findAnalysisMetaByAnalysisId(UUID)`, `List<AnalysisMetaEntity> findAllAnalysisMetas()`, `void save(AnalysisMetaEntity)`, `void delete(UUID)`.

- [ ] **Step 1: Create the `AnalysisStatus` enum**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.anki;

public enum AnalysisStatus {
  NEW,
  DB_UPLOADED,
  DECK_SELECTED,
  MARKED_FOR_DELETION,
}
```

- [ ] **Step 2: Create `AnalysisMetaEntity`**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.MetaKeys;
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
public class AnalysisMetaEntity {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String pk;

    @Getter(onMethod_ = @DynamoDbSortKey)
    private String sk;

    private String dbPath;
    private Long deckId;
    private String deckName;
    private AnalysisStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public AnalysisMetaEntity(UUID analysisId, AnalysisStatus status) {
        this.pk = AnalysisKeys.analysisPk(analysisId);
        this.sk = MetaKeys.metaSk();
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getAnalysisId() {
        return UUID.fromString(pk.substring("ANALYSIS#".length()));
    }
}
```

- [ ] **Step 3: Create `AnalysisMetaRepository`**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.MetaKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AnalysisMetaRepository {

  private final DynamoDbTable<AnalysisMetaEntity> analysisMetaTable;

  public Optional<AnalysisMetaEntity> findAnalysisMetaByAnalysisId(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(MetaKeys.metaSk())
            .build();

    return Optional.ofNullable(analysisMetaTable.getItem(key));
  }

  public List<AnalysisMetaEntity> findAllAnalysisMetas() {
    Expression filter =
        Expression.builder()
            .expression("#sk = :sk")
            .expressionNames(Map.of("#sk", "sk"))
            .expressionValues(Map.of(":sk", AttributeValue.fromS(MetaKeys.metaSk())))
            .build();

    return analysisMetaTable
        .scan(ScanEnhancedRequest.builder().filterExpression(filter).build())
        .items()
        .stream()
        .toList();
  }

  public void save(AnalysisMetaEntity entity) {
    analysisMetaTable.putItem(entity);
  }

  public void delete(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(MetaKeys.metaSk())
            .build();
    analysisMetaTable.deleteItem(key);
  }
}
```

- [ ] **Step 4: Register the table bean in `DynamoDbClientConfig`**

Add the import:

```java
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisMetaEntity;
```

Add this method (after the `bulkFormatTable` bean):

```java
  @Bean
  DynamoDbTable<AnalysisMetaEntity> analysisMetaTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(AnalysisMetaEntity.class));
  }
```

- [ ] **Step 5: Compile**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: exit 0, no output.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisStatus.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaEntity.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaRepository.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java
git commit -m "add AnalysisMetaEntity and repository for DynamoDB"
```

---

### Task 3: Add domain classes `Analysis` and `BulkFormat`

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/anki/Analysis.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormat.java`

**Interfaces:**
- Consumes: `AnalysisStatus` (Task 2, `dynamodb.anki`), `BulkFormatStatus` (`infrastructure.dynamodb.anki`, existing).
- Produces (used by Task 4 and Task 5):
  - `Analysis` — fields `analysisId` (UUID), `status`, `deckId` (Long, nullable), `deckName`, `dbPath` (Path), `createdAt` (Instant), `updatedAt` (Instant), `Optional<BulkFormat> bulkFormat`; `@Getter`/`@Setter`; constructor `Analysis(UUID, AnalysisStatus, Long, String, Path, Instant, Instant, Optional<BulkFormat>)`.
  - `BulkFormat` — fields `status` (`BulkFormatStatus`), `createdAt`, `lastUpdatedAt`, `totalNotes` (int), `completedNotes` (int); `@Getter`/`@Setter`; constructor `BulkFormat(BulkFormatStatus, Instant, Instant, int, int)`.

- [ ] **Step 1: Create `BulkFormat`**

```java
package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkFormat {
  private BulkFormatStatus status;
  private Instant createdAt;
  private Instant lastUpdatedAt;
  private int totalNotes;
  private int completedNotes;
}
```

- [ ] **Step 2: Create `Analysis`**

```java
package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisStatus;
import java.nio.file.Path;
import java.time.Instant;
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
public class Analysis {
  private UUID analysisId;
  private AnalysisStatus status;
  private Long deckId;
  private String deckName;
  private Path dbPath;
  private Instant createdAt;
  private Instant updatedAt;
  private Optional<BulkFormat> bulkFormat = Optional.empty();
}
```

- [ ] **Step 3: Compile**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: exit 0, no output.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/Analysis.java src/main/java/com/felixkroemer/smort/domain/anki/BulkFormat.java
git commit -m "add Analysis and BulkFormat domain classes"
```

---

### Task 4: Rewrite `AnalysisService` and update its consumers

**Files:**
- Rewrite: `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/sqlite/anki/EntityManagerFactoryCache.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepository.java` (add `delete`)
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/AnalysisMapper.java`

**Interfaces:**
- Consumes: `AnalysisMetaRepository` + `AnalysisMetaEntity` (Task 2), `Analysis` + `BulkFormat` (Task 3), `BulkFormatRepository` (existing).
- Produces (used by Task 5):
  - `AnalysisService.getAnalysis(UUID)` → `Analysis`
  - `AnalysisService.getAnalyses()` → `List<Analysis>`
  - `AnalysisService.createAnalysis()` → `UUID` (unchanged signature)
  - `AnalysisService.uploadDB(UUID, byte[])`, `setDeck(UUID, Long)`, `deleteAnalysis(UUID)` — unchanged signatures, no `@Transactional`
  - `BulkFormatRepository.delete(UUID)` — new
  - `AnalysisMapper.toAnalysisResponse(Analysis)` and `List<Analysis>` — now domain-typed

- [ ] **Step 1: Rewrite `AnalysisService.java`**

Replace the entire file. All `@Transactional`/`TransactionUtil` usage is gone; file cleanup happens in an explicit catch. Entity fields are now `String dbPath` and `Long deckId`; domain conversion via private helpers.

```java
package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.config.SmortProperties;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.sqlite.anki.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

  private final AnalysisMetaRepository analysisMetaRepository;
  private final BulkFormatRepository bulkFormatRepository;
  private final AnkiNoteRepository ankiNoteRepository;
  private final AnkiNoteTypeService noteTypeService;
  private final DerivedNoteRepository derivedNoteRepository;

  private final SmortProperties smortProperties;

  public UUID createAnalysis() {
    var analysis = new AnalysisMetaEntity(UUID.randomUUID(), AnalysisStatus.NEW);
    analysisMetaRepository.save(analysis);
    log.info("Started new analysis. id={}", analysis.getAnalysisId());
    return analysis.getAnalysisId();
  }

  public Analysis getAnalysis(UUID analysisId) {
    var bulkFormat =
        bulkFormatRepository.findBulkFormatByAnalysisId(analysisId).map(this::toBulkFormat);
    return toAnalysis(getMeta(analysisId), bulkFormat);
  }

  public List<Analysis> getAnalyses() {
    return analysisMetaRepository.findAllAnalysisMetas().stream()
        .map(
            entity ->
                toAnalysis(
                    entity,
                    bulkFormatRepository
                        .findBulkFormatByAnalysisId(entity.getAnalysisId())
                        .map(this::toBulkFormat)))
        .toList();
  }

  public void uploadDB(UUID analysisId, byte[] bytes) {
    var entity = getMeta(analysisId);

    if (bytes == null || bytes.length == 0) {
      throw new SmortException("Empty upload for analysis. id={}", analysisId);
    }
    if (bytes.length > smortProperties.getAnalysisMaxDbSize()) {
      throw new SmortException("Anki DB upload too large. id={}", analysisId);
    }

    if (entity.getStatus() != AnalysisStatus.NEW) {
      throw new SmortException(
          "Analysis is not in NEW state. id={}, status={}", analysisId, entity.getStatus());
    }

    var dbPath = smortProperties.getAnkiDbDirectory().resolve(analysisId.toString());
    try {
      Files.createDirectories(dbPath.getParent());
      Files.write(dbPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      throw new SmortException(e);
    }

    try {
      entity.setDbPath(dbPath.toString());
      entity.setStatus(AnalysisStatus.DB_UPLOADED);
      analysisMetaRepository.save(entity);
    } catch (Exception e) {
      try {
        log.warn("Failed to persist analysis meta, deleting uploaded db. id={}, db={}", analysisId, dbPath);
        Files.deleteIfExists(dbPath);
      } catch (Exception cleanupException) {
        log.error("Failed to delete db after save failure. id={}", analysisId, cleanupException);
      }
      throw e;
    }

    log.info(
        "Upload complete for analysis. id={}, size={}KB", analysisId, bytes.length / 1024.0);
  }

  public void setDeck(UUID analysisId, Long deckId) {
    var entity = getMeta(analysisId);

    if (entity.getStatus() != AnalysisStatus.DB_UPLOADED) {
      throw new SmortException(
          "Analysis is not in DB_UPLOADED state. id={}, status={}",
          analysisId,
          entity.getStatus());
    }

    var deck =
        getDecks(analysisId).stream()
            .filter(d -> d.getId().equals(deckId))
            .findAny()
            .orElseThrow(
                () -> new SmortException("Deck not found. id={}, deckId={}", analysisId, deckId));

    entity.setStatus(AnalysisStatus.DECK_SELECTED);
    entity.setDeckId(deckId);
    entity.setDeckName(deck.getName());
    analysisMetaRepository.save(entity);
  }

  public List<AnkiNote> getNotes(UUID analysisId) {
    var analysis = getAnalysis(analysisId);

    var noteTypes = noteTypeService.getNoteTypesByAnalysisId(analysisId);
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    return notes.stream()
        .map(
            n -> {
              var noteType = noteTypes.get(n.getNoteTypeId());
              var noteTypeFieldNames = noteType.getFields();
              var fields =
                  IntStream.range(0, noteTypeFieldNames.size())
                      .boxed()
                      .collect(Collectors.toMap(noteTypeFieldNames::get, n.getFlds()::get));
              return new AnkiNote(n.getId(), fields, n.getGuid(), n.getNoteTypeId());
            })
        .toList();
  }

  public List<AnkiDeckEntity> getDecks(UUID analysisId) {
    return ankiNoteRepository.findDecksByAnalysisId(analysisId);
  }

  public List<DerivedNoteEntity> getDerivedNotes(UUID analysisId) {
    return derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId);
  }

  public List<AnkiNoteTypeEntity> getNoteTypes(UUID analysisId) {
    var notes = getNotes(analysisId);
    var deckNoteTypeIds = notes.stream().map(AnkiNote::getNoteTypeId).collect(Collectors.toSet());
    var allNoteTypes = ankiNoteRepository.findNoteTypesByAnalysisId(analysisId);
    return allNoteTypes.stream()
        .filter(noteType -> deckNoteTypeIds.contains(noteType.getId()))
        .toList();
  }

  public Map<DerivedNoteEntity, String> getDerivedNoteToGuidMapping(
      UUID analysisId, List<DerivedNoteEntity> derivedNotes) {
    var derivedNoteIds =
        derivedNotes.stream().map(DerivedNoteEntity::getNoteId).collect(Collectors.toSet());
    var guidByNoteId =
        ankiNoteRepository.findNotesByAnalysisIdAndNoteIdIn(analysisId, derivedNoteIds).stream()
            .collect(Collectors.toMap(AnkiNoteEntity::getId, AnkiNoteEntity::getGuid));

    return derivedNotes.stream()
        .collect(Collectors.toMap(Function.identity(), d -> guidByNoteId.get(d.getNoteId())));
  }

  // TODO: retry failed deletion attempts
  public void deleteAnalysis(UUID analysisId) {
    var entity = getMeta(analysisId);
    entity.setStatus(AnalysisStatus.MARKED_FOR_DELETION);
    analysisMetaRepository.save(entity);
    try {
      if (entity.getDbPath() != null) {
        Files.deleteIfExists(Path.of(entity.getDbPath()));
      }
      derivedNoteRepository.deleteAnalysisDerivedNotes(analysisId);
      bulkFormatRepository.delete(analysisId);
      analysisMetaRepository.delete(analysisId);
    } catch (Exception e) {
      log.warn("Could not fully delete analysis. analysisId={}", analysisId, e);
    }
  }

  private AnalysisMetaEntity getMeta(UUID analysisId) {
    return analysisMetaRepository
        .findAnalysisMetaByAnalysisId(analysisId)
        .orElseThrow(() -> new SmortException("Could not find analysis by id. id={}", analysisId));
  }

  private Analysis toAnalysis(AnalysisMetaEntity entity, Optional<BulkFormat> bulkFormat) {
    return new Analysis(
        entity.getAnalysisId(),
        entity.getStatus(),
        entity.getDeckId(),
        entity.getDeckName(),
        entity.getDbPath() != null ? Path.of(entity.getDbPath()) : null,
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        bulkFormat);
  }

  private BulkFormat toBulkFormat(BulkFormatEntity entity) {
    return new BulkFormat(
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getLastUpdatedAt(),
        entity.getTotalNotes(),
        entity.getCompletedNotes());
  }
}
```

- [ ] **Step 2: Update `EntityManagerFactoryCache` to use `AnalysisMetaRepository`**

Replace the field and constructor-injected dependency:

```java
  private final AnalysisMetaRepository analysisMetaRepository;
```

Replace the imports:

```java
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisMetaRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisStatus;
```

Replace the look-up block (lines ~44-56):

```java
    var analysis =
        analysisMetaRepository
            .findAnalysisMetaByAnalysisId(analysisId)
            .orElseThrow(
                () -> new SmortException("Could not find analysis by id. id={}", analysisId));

    if (analysis.getStatus() == AnalysisStatus.NEW) {
      throw new SmortException("Analysis is not ready. id={}", analysisId);
    }

    var dbPath = Path.of(analysis.getDbPath());
```

Note: `analysis.getDbPath()` now returns a `String`, so wrap with `Path.of(...)` (change the later `ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath())` line only if it doesn't already take a `Path` — `dbPath` is now a `Path` again after the wrap, so that line is unchanged).

- [ ] **Step 3: Add `delete(UUID)` to `BulkFormatRepository`**

Add this method (after `save`):

```java
  public void delete(UUID analysisId) {
    var key =
        Key.builder()
            .partitionValue(AnalysisKeys.analysisPk(analysisId))
            .sortValue(BulkFormatKeys.bulkFormatSk())
            .build();
    bulkFormatTable.deleteItem(key);
  }
```

- [ ] **Step 4: Update `AnalysisMapper` to map the domain `Analysis`**

Replace the entire file:

```java
package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.AnalysisResponse;
import com.felixkroemer.smort.domain.anki.Analysis;
import java.util.List;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnalysisMapper {

  @Mapping(source = "analysisId", target = "id")
  AnalysisResponse toAnalysisResponse(Analysis analysis);

  List<AnalysisResponse> toAnalysisResponse(List<Analysis> analysis);

  default Optional<String> longToOptionalString(Long value) {
    return Optional.ofNullable(value).map(String::valueOf);
  }
}
```

(Enum → String and `Long` → `Optional<String>` via `longToOptionalString` are implicit MapStruct conversions; `@Mapping` is required because the target field is `id` while the source is `analysisId`.)

- [ ] **Step 5: Compile**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: exit 0, no output. (`DeckService` and `BulkFormatService` still compile unchanged: they only use `getDeckId()`/`getDeckName()`, which the domain `Analysis` provides.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java src/main/java/com/felixkroemer/smort/infrastructure/sqlite/anki/EntityManagerFactoryCache.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/BulkFormatRepository.java src/main/java/com/felixkroemer/smort/application/anki/AnalysisMapper.java
git commit -m "use DynamoDB analysis meta with domain objects in service layer"
```

---

### Task 5: `BulkFormatService` returns the domain `BulkFormat`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java`

**Interfaces:**
- Consumes: `Analysis` + `BulkFormat` domain classes (Task 3), `AnalysisService.getAnalysis` (Task 4, returns `Analysis`), `BulkFormatEntity`.
- Produces: `BulkFormatService.getJobStatus(UUID)` → `BulkFormat`. (The controller's `getBulkFormatStatus` calls `getStatus().name()`, `getCreatedAt()`, `getLastUpdatedAt()`, `getTotalNotes()`, `getCompletedNotes()` on it — all present on the domain class, so `AnalysisController` needs no change.)

- [ ] **Step 1: Update imports**

Add:

```java
import com.felixkroemer.smort.domain.anki.BulkFormat;
```

The existing `import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;` already covers `BulkFormatEntity` and `BulkFormatStatus`.

- [ ] **Step 2: Change `getJobStatus` to return the domain type and add the mapper**

Replace:

```java
  public BulkFormatEntity getJobStatus(UUID analysisId) {
    return bulkFormatRepository
        .findBulkFormatByAnalysisId(analysisId)
        .orElseThrow(
            () -> new SmortException("No bulk format job found. analysisId={}", analysisId));
  }
```

with:

```java
  public BulkFormat getJobStatus(UUID analysisId) {
    return bulkFormatRepository
        .findBulkFormatByAnalysisId(analysisId)
        .map(this::toBulkFormat)
        .orElseThrow(
            () -> new SmortException("No bulk format job found. analysisId={}", analysisId));
  }

  private BulkFormat toBulkFormat(BulkFormatEntity entity) {
    return new BulkFormat(
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getLastUpdatedAt(),
        entity.getTotalNotes(),
        entity.getCompletedNotes());
  }
```

No other changes: `startBulkFormat`/`resumeBulkFormat`/`processNotes` only call `analysisService.getAnalysis(analysisId).getDeckId()`, which the domain `Analysis` provides.

- [ ] **Step 3: Compile**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: exit 0, no output.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/BulkFormatService.java
git commit -m "return domain BulkFormat from BulkFormatService"
```

---

### Task 6: Remove the Postgres/JPA stack

**Files:**
- Delete: `src/main/java/com/felixkroemer/smort/infrastructure/postgres/anki/AnalysisEntity.java`
- Delete: `src/main/java/com/felixkroemer/smort/infrastructure/postgres/anki/AnalysisRepository.java`
- Delete: `src/main/java/com/felixkroemer/smort/infrastructure/postgres/anki/AnalysisStatus.java`
- Delete: `src/main/java/com/felixkroemer/smort/infrastructure/postgres/common/AuditEntity.java`
- Delete: `src/main/java/com/felixkroemer/smort/common/util/TransactionUtil.java`
- Delete: `src/main/resources/db/changelog/db.changelog-master.yaml`
- Delete: `src/main/resources/db/changelog/changesets/0.0.1-create-analysis-table.yaml`
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/resources/application-local.properties`
- Modify: `docker-compose.yaml`
- Modify: `src/main/java/com/felixkroemer/smort/SmortApplication.java`

**Interfaces:**
- Consumes: everything from Tasks 1-5.
- Produces: a project with no Postgres, no Liquibase, no Spring Data JPA, no `spring.datasource.*` properties.

- [ ] **Step 1: Delete the Postgres package, `TransactionUtil`, and Liquibase changesets**

```bash
git rm -r src/main/java/com/felixkroemer/smort/infrastructure/postgres
git rm src/main/java/com/felixkroemer/smort/common/util/TransactionUtil.java
git rm -r src/main/resources/db/changelog
```

- [ ] **Step 2: Update `pom.xml`**

Remove these five dependencies entirely:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-liquibase</artifactId>
        </dependency>
```
```xml
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
```
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-liquibase-test</artifactId>
            <scope>test</scope>
        </dependency>
```

Add these three (versions are managed by the `spring-boot-starter-parent` BOM — do not set versions):

```xml
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-orm</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-core</artifactId>
        </dependency>
```

These keep `EntityManagerFactoryCache` working (`LocalContainerEntityManagerFactoryBean`, `HibernateJpaVendorAdapter`, `DelegatingDataSource`, and the `jakarta.persistence` annotations on the sqlite entities).

- [ ] **Step 3: Update `application.properties`**

Remove lines 4-6 and 11 (the datasource and liquibase config). Result:

```properties
spring.application.name=smort
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
smort.base-data-dir=${BASE_DATA_DIR}
smort.anki.analysis.db-directory-name=${ANALYSIS_DB_DIRECTORY_NAME}
smort.anki.analysis.max-db-size=${ANALYSIS_MAX_DB_SIZE}
openai.model=${OPENAI_MODEL:gpt-4o}
app.scheduling.bulk-format-cron:"*/1 * * * *"
```

- [ ] **Step 4: Update `application-local.properties`**

Remove lines 1-3 (datasource). Result:

```properties
smort.base-data-dir=.smort
smort.anki.analysis.db-directory-name=anki/db
smort.anki.analysis.max-db-size=52428800
```

- [ ] **Step 5: Update `docker-compose.yaml`**

Remove only the postgres service block. Result:

```yaml
services:
  dynamodb-local:
    image: amazon/dynamodb-local
    container_name: dynamodb-local
    command: "-jar DynamoDBLocal.jar -sharedDb"
    ports:
      - "8000:8000"
```

- [ ] **Step 6: Update `SmortApplication`**

Remove `@EnableJpaAuditing` and exclude Boot's JDBC auto-configuration (spring-orm transitively brings in spring-jdbc, so `DataSourceAutoConfiguration` would otherwise try to create a datasource and fail without `spring.datasource.url`):

```java
package com.felixkroemer.smort;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableScheduling
public class SmortApplication {

  public static void main(String[] args) {
    SpringApplication.run(SmortApplication.class, args);
  }
}
```

- [ ] **Step 7: Compile**

Run: `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2 PATH=/home/fekr/.jdks/corretto-25.0.2/bin:$PATH ./mvnw compile -q`
Expected: exit 0, no output.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "remove Postgres and JPA stack; use DynamoDB for analysis meta"
```

---

## Self-Review Notes

- **Spec coverage:** every spec section maps to a task — entities/keys (1, 2), repositories (2, 4), domain classes (3), AnalysisService + EntityManagerFactoryCache + deleteAnalysis (4), mapper (4), BulkFormatService (5), Postgres/JPA removal incl. pom/props/compose/liquibase/SmortApplication (6).
- **Compile invariant:** Tasks 1-3 add only new code. Task 4 rewrites the service AND its breaking consumers (mapper, controller path, EntityManagerFactoryCache) together. Task 5 compiles after 4 (`DeckService`/`BulkFormatService` only touch `getDeckId()`/`getDeckName()`/`getStatus()` which the domain provides).
- **Type consistency:** `AnalysisMetaEntity.dbPath` is `String` everywhere in infra; domain `Analysis.dbPath` is `Path`; conversion only in `AnalysisService.toAnalysis` and `EntityManagerFactoryCache`. `BulkFormatKeys.bulkFormatSk()` returns `"META#BULKFORMAT#"` once (Task 1) and is reused by the repo + entity.

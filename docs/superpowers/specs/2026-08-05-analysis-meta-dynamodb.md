# Move Analysis Session State to DynamoDB

**Date:** 2026-08-05

**Status:** Approved design

## Goal

Move the analysis session state out of Postgres/JPA and into DynamoDB, mirroring the existing single-table design. The current `AnalysisEntity` (JPA, Postgres `analysis` table) becomes `AnalysisMetaEntity` (DynamoDB, `ANALYSIS#<analysisId>` / `META#`). It remains a separate item from `BulkFormatEntity`, which keeps tracking bulk-format state under the same partition with sort key `META#BULKFORMAT#`. The service layer stops receiving the raw entity and instead receives a domain object that embeds the bulk-format state as `Optional<BulkFormat>`.

The entire Postgres/JPA stack is removed from the project.

## Decisions (from brainstorming)

| Question | Decision |
|---|---|
| Postgres/JPA removal scope | **Full removal** — delete JPA starter, postgres driver, Liquibase, datasource props, docker-compose postgres, AuditEntity; keep sqlite cache working via explicit spring-orm/hibernate deps |
| Existing data | **Start fresh** — no migration; Postgres `analysis` rows and old `BULKFORMAT#` items are discarded |
| Bulk-format type in domain | **Domain-typed** — a small domain `BulkFormat` class, not the raw `BulkFormatEntity`, is embedded in the domain `Analysis` |
| List endpoint bulk format | **Embed for all** — `getAnalyses()` fetches each analysis's bulk format too |
| Composition location | **Service composes** — `AnalysisService` injects both repositories and builds the domain object |
| `uploadDB` failure handling | **Explicit cleanup** — no Spring transaction; delete the written file in a catch block if the DynamoDB save fails |
| Storage approach | **Two items, service composes** — rejected nested-attributes (breaks `StatusBulkFormatIndex` GSI, contradicts two-entity requirement) and single-partition-query (typed query can't deserialize two bean types cleanly) |

## Architecture

Single DynamoDB table (`common-table`). The `ANALYSIS#<analysisId>` partition contains:

| Sort key | Entity | Purpose |
|---|---|---|
| `META#` | `AnalysisMetaEntity` | Session state: db path, deck, status, timestamps |
| `META#BULKFORMAT#` | `BulkFormatEntity` | Bulk-format job state + `StatusBulkFormatIndex` GSI |
| `NOTE#<noteId>` | `DerivedNoteEntity` | Derived (formatted) notes (existing) |

## Component design

### 1. `AnalysisMetaEntity` — new, `infrastructure/dynamodb/anki/`

`@DynamoDbBean` with:
- `pk` — `ANALYSIS#<analysisId>` via `AnalysisKeys.analysisPk(UUID)`
- `sk` — `META#` via `MetaKeys.metaSk()`
- `dbPath` — `String` (nullable; the DynamoDB enhanced client cannot map `java.nio.file.Path` directly, so the raw path string is stored and converted to/from `Path` in the service layer)
- `deckId` — `Long` (nullable; the sqlite deck id, distinct from DynamoDB `DECK#` UUIDs)
- `deckName` — `String`
- `status` — `AnalysisStatus`
- `createdAt` / `updatedAt` — `Instant`, set manually in the constructor (no more Spring Data auditing)
- `getAnalysisId()` — derives UUID from pk, same pattern as `BulkFormatEntity.getAnalysisId()`
- Constructor `AnalysisMetaEntity(UUID analysisId, AnalysisStatus status)` sets pk/sk/createdAt/updatedAt

`AnalysisStatus` enum moves from `infrastructure.postgres.anki` to `infrastructure.dynamodb.anki`. Values unchanged: `NEW`, `DB_UPLOADED`, `DECK_SELECTED`, `MARKED_FOR_DELETION`.

`DynamoDbClientConfig` registers an `analysisMetaTable` bean via `TableSchema.fromBean(AnalysisMetaEntity.class)`.

### 2. `BulkFormatEntity` — modified, `infrastructure/dynamodb/anki/`

- Sort key changes from `BULKFORMAT#` to `META#BULKFORMAT#`
- `BulkFormatKeys.bulkFormatSk()` and `bulkFormatPrefix()` return `"META#BULKFORMAT#"`
- Everything else (fields, GSI, `getAnalysisId()`) unchanged

### 3. `AnalysisMetaRepository` — new, `infrastructure/dynamodb/anki/`

- `Optional<AnalysisMetaEntity> findAnalysisMetaByAnalysisId(UUID)` — `getItem(pk=ANALYSIS#id, sk=META#)`
- `List<AnalysisMetaEntity> findAllAnalysisMetas()` — scan filtered on `sk = "META#"` (analogue of JPA `findAll`; exact-match on the sort key excludes `META#BULKFORMAT#` and all other item types)
- `void save(AnalysisMetaEntity)`
- `void delete(UUID analysisId)` — delete by key

`BulkFormatRepository` is unchanged except for the key constant. A `delete(UUID analysisId)` method is added for `deleteAnalysis`.

### 4. Domain classes — new, `domain/anki/`

- `Analysis` — `analysisId` (UUID), `status`, `deckId` (Long, nullable), `deckName`, `dbPath` (Path), `createdAt`, `updatedAt`, `Optional<BulkFormat> bulkFormat`. `@Getter`/`@Setter` mutable, mirroring current service usage.
- `BulkFormat` — `status` (`BulkFormatStatus`), `createdAt`, `lastUpdatedAt`, `totalNotes` (int), `completedNotes` (int).

`AnalysisService` builds `Analysis` from an `AnalysisMetaEntity` + `Optional<BulkFormatEntity>` (mapping `BulkFormatEntity` → `BulkFormat`). The domain `Analysis.dbPath` remains `Path`; conversion between the entity's `String` and the domain's `Path` happens in `AnalysisService`. The entity gets a `setDbPath(String)` (service converts), and `EntityManagerFactoryCache` wraps the entity's string with `Path.of(...)`.

### 5. `AnalysisService` — modified, `domain/anki/`

Injects `AnalysisMetaRepository` + `BulkFormatRepository` (replaces `AnalysisRepository`).

- `createAnalysis()` — new `AnalysisMetaEntity(id, NEW)`, save, return id
- `getAnalysis(UUID)` — fetch meta + bulk format, compose `Analysis`; `SmortException` if meta not found
- `getAnalyses()` — `findAllAnalysisMetas()` + per-analysis bulk format fetch, compose list
- `uploadDB(UUID, byte[])` — drop `@Transactional`/`TransactionUtil`; size check; status must be `NEW`; write file; set `dbPath` + `DB_UPLOADED`; save; **on save failure delete the file in a catch block**
- `setDeck(UUID, Long)` — status must be `DB_UPLOADED`; validate deck; set `deckId`/`deckName`/`DECK_SELECTED`; save
- `deleteAnalysis(UUID)` — fetch entity; set `MARKED_FOR_DELETION`; save; delete db file if present; delete derived notes (`DerivedNoteRepository.deleteAnalysisDerivedNotes`); delete meta item and bulk-format item
- `getNotes` / `getDecks` / `getDerivedNotes` / `getNoteTypes` / `getDerivedNoteToGuidMapping` — consume the `Analysis` domain object via `getAnalysis()` where they need `deckId`

### 6. `BulkFormatService` — modified, `domain/anki/`

- Keeps `BulkFormatRepository`; `analysisService.getAnalysis(id)` returns the domain `Analysis` (used for `deckId`)
- `getJobStatus(UUID)` returns domain `BulkFormat` instead of `BulkFormatEntity`

### 7. `EntityManagerFactoryCache` — modified, `infrastructure/sqlite/anki/`

- Injects `AnalysisMetaRepository` instead of `AnalysisRepository`
- Same logic: status must not be `NEW`; use `dbPath` to open the sqlite file

### 8. Controller & mapper — modified, `application/anki/`

- `AnalysisMapper` (MapStruct) maps domain `Analysis` → `AnalysisResponse` instead of the entity
- `AnalysisResponse` and all REST endpoints remain unchanged; bulk format stays exposed via the existing `/format/status` endpoint

## Postgres/JPA removal

- Delete `infrastructure/postgres/` package entirely (`AnalysisEntity`, `AnalysisRepository`, `AuditEntity`; `AnalysisStatus` lives on in `dynamodb.anki`)
- `pom.xml`:
  - Remove `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `spring-boot-starter-liquibase`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-liquibase-test`
  - Add explicit `spring-orm`, `hibernate-core`, `spring-jdbc` (used by `EntityManagerFactoryCache`: `LocalContainerEntityManagerFactoryBean`, `HibernateJpaVendorAdapter`, `DelegatingDataSource`, and the sqlite entities' `jakarta.persistence` annotations)
- `application.properties` / `application-local.properties`: remove `spring.datasource.*` and `spring.liquibase.*`
- `docker-compose.yaml`: remove the postgres service block, keep `dynamodb-local`
- Delete `src/main/resources/db/changelog/`
- Delete `common/util/TransactionUtil.java` (only used by `AnalysisService`)

## Error handling

- Missing `AnalysisMetaEntity` → `SmortException("Could not find analysis by id...")`, same as today
- State-machine guards (`NEW` → `DB_UPLOADED` → `DECK_SELECTED`) unchanged
- `uploadDB` file cleanup on failed save: catch, `Files.deleteIfExists(dbPath)`, log warning, rethrow

## Testing / verification

- `./mvnw compile` — compile clean (with `JAVA_HOME=/home/fekr/.jdks/corretto-25.0.2`)
- `./mvnw test` — context-load smoke test (`SmortApplicationTests`) is pre-existing-broken in this environment (requires `POSTGRES_URL` + the `local` DynamoDB profile) and is not part of this work's gate; the datasource failure it hits disappears once `spring.datasource.*` is removed
- No unit tests exist for the affected classes; behavior is verified by compile, consistent with the current repo state

## Out of scope

- No data migration (start fresh)
- No changes to REST API shape
- No changes to the `StatusBulkFormatIndex` GSI or the bulk-format cron

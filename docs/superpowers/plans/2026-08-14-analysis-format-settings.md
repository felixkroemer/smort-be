# Analysis Format Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-analysis `formatInstructions` setting stored on the Analysis entity, exposed via `GET` and `PATCH /analysis/{analysisId}/settings`.

**Architecture:** A single `Optional<String> formatInstructions` field is added to the DynamoDB `AnalysisMetaEntity` (persisted via the existing `OptionalStringConverter`) and the domain `Analysis` POJO. `AnalysisService` gains a getter/setter pair; `AnalysisController` exposes two endpoints. DTOs are records; a MapStruct method in `AnalysisRestMapper` converts the `Optional` to the response record. Jackson 3 (Boot 4) handles `Optional` natively, same as the existing `AnalysisResponse.deckId`.

**Tech Stack:** Spring Boot 4.0.3, Java 25, AWS DynamoDB Enhanced Client, MapStruct, Lombok.

## Global Constraints

- Work only on branch `feat/analysis-format-settings`; never commit to `main`.
- No unit tests exist in this repo (deliberately removed in earlier commits). The user-approved spec calls for build + manual verification against local DynamoDB. Do NOT add unit tests.
- Field name everywhere: `formatInstructions`, type `Optional<String>`.
- Entity field must use `@Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))` exactly like `AbstractChatMessageEntity` (`src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/chat/AbstractChatMessageEntity.java:22-37`).
- PATCH semantics (must hold): absent key in body = leave unchanged; explicit JSON `null` = clear; non-null string = overwrite.
- Not wired into OpenAI formatting.

---

### Task 1: Add `formatInstructions` to persistence and domain layers

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaEntity.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/Analysis.java`

**Interfaces:**
- Produces: `AnalysisMetaEntity.getFormatInstructions()` / `setFormatInstructions(Optional<String>)` (Lombok-generated). `Analysis.formatInstructions` field (Lombok getter/setter).

- [ ] **Step 1: Add the field to `AnalysisMetaEntity`**

Add imports after the existing imports (line 4, after `MetaKeys` import):

```java
import com.felixkroemer.smort.infrastructure.dynamodb.OptionalStringConverter;
```

and (after line 6 `import java.util.UUID;`):

```java
import java.util.Optional;
```

Add the field after the `updatedAt` field (line 29), mirroring `AbstractChatMessageEntity`:

```java
  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  private Optional<String> formatInstructions;
```

- [ ] **Step 2: Add the field to the domain `Analysis`**

Add `import java.util.Optional;` if not already present (it is — line 6). Add after the `bulkFormat` field (line 25):

```java
  private Optional<String> formatInstructions = Optional.empty();
```

- [ ] **Step 3: Compile**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS. `AnalysisEntityMapper` maps the new field automatically by name; `unmappedTargetPolicy = ERROR` is satisfied because both source and target have the field.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaEntity.java src/main/java/com/felixkroemer/smort/domain/anki/Analysis.java
git commit -m "feat: add formatInstructions to analysis entity"
```

---

### Task 2: Add settings methods to `AnalysisService`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java`

**Interfaces:**
- Consumes: `AnalysisMetaRepository.findAnalysisMetaByAnalysisId(UUID)` → `Optional<AnalysisMetaEntity>`; `AnalysisMetaEntity.setFormatInstructions(Optional<String>)`; `AnalysisMetaEntity.setUpdatedAt(Instant)`; `analysisMetaRepository.save(entity)`.
- Produces: `public Optional<String> getFormatSettings(UUID analysisId)`; `public void updateFormatSettings(UUID analysisId, Optional<String> formatInstructions)`. Both throw `NotFoundException` (via existing private `getMeta`) when the analysis does not exist.

- [ ] **Step 1: Add imports**

Add to the import block (after line 12 `import java.nio.file.StandardOpenOption;`):

```java
import java.time.Instant;
```

and (after line 15 `import java.util.List;`):

```java
import java.util.Optional;
```

- [ ] **Step 2: Add the two methods**

Add directly after `getAnalyses()` (which ends around line 70) and before `uploadDB`:

```java
  public Optional<String> getFormatSettings(UUID analysisId) {
    return Optional.ofNullable(getMeta(analysisId).getFormatInstructions());
  }

  public void updateFormatSettings(UUID analysisId, Optional<String> formatInstructions) {
    var analysis = getMeta(analysisId);
    analysis.setFormatInstructions(formatInstructions);
    analysis.setUpdatedAt(Instant.now());
    analysisMetaRepository.save(analysis);
  }
```

`Optional.ofNullable` normalizes items persisted before this feature (the attribute is absent on old items, so the field reads back `null`).

- [ ] **Step 3: Compile**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java
git commit -m "feat: add get and update format settings to AnalysisService"
```

---

### Task 3: Add DTOs, REST mapper method, and controller endpoints

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/anki/dto/FormatSettingsResponse.java`
- Create: `src/main/java/com/felixkroemer/smort/application/anki/dto/UpdateFormatSettingsRequest.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/mapping/AnalysisRestMapper.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java`

**Interfaces:**
- Consumes: `AnalysisService.getFormatSettings(UUID)` → `Optional<String>`; `AnalysisService.updateFormatSettings(UUID, Optional<String>)`.
- Produces: `GET /analysis/{analysisId}/settings` and `PATCH /analysis/{analysisId}/settings`; `FormatSettingsResponse(Optional<String> formatInstructions)`; `UpdateFormatSettingsRequest(Optional<String> formatInstructions)`; `AnalysisRestMapper.toFormatSettingsResponse(Optional<String>)`.

- [ ] **Step 1: Create `FormatSettingsResponse`**

```java
package com.felixkroemer.smort.application.anki.dto;

import java.util.Optional;

public record FormatSettingsResponse(Optional<String> formatInstructions) {}
```

- [ ] **Step 2: Create `UpdateFormatSettingsRequest`**

```java
package com.felixkroemer.smort.application.anki.dto;

import java.util.Optional;

public record UpdateFormatSettingsRequest(Optional<String> formatInstructions) {}
```

- [ ] **Step 3: Add the mapper method to `AnalysisRestMapper`**

Add import (after line 2 `import com.felixkroemer.smort.application.anki.dto.AnalysisResponse;`):

```java
import com.felixkroemer.smort.application.anki.dto.FormatSettingsResponse;
```

Add method after `toAnalysisResponse` list overload (line 17):

```java
  FormatSettingsResponse toFormatSettingsResponse(Optional<String> formatInstructions);
```

- [ ] **Step 4: Add the controller endpoints**

Add import (with the existing `application.anki.dto` imports at the top of the file):

```java
import com.felixkroemer.smort.application.anki.dto.FormatSettingsResponse;
import com.felixkroemer.smort.application.anki.dto.UpdateFormatSettingsRequest;
```

Add methods to `AnalysisController` (e.g., after `getAnalysis`, which is the `@GetMapping("/{analysisId}")` handler):

```java
  @GetMapping("/{analysisId}/settings")
  public FormatSettingsResponse getFormatSettings(@PathVariable("analysisId") UUID analysisId) {
    return analysisRestMapper.toFormatSettingsResponse(analysisService.getFormatSettings(analysisId));
  }

  @PatchMapping("/{analysisId}/settings")
  public FormatSettingsResponse updateFormatSettings(
      @PathVariable("analysisId") UUID analysisId,
      @RequestBody UpdateFormatSettingsRequest updateFormatSettingsRequest) {
    var formatInstructions = updateFormatSettingsRequest.formatInstructions();
    if (formatInstructions != null) {
      analysisService.updateFormatSettings(analysisId, formatInstructions);
    }
    return analysisRestMapper.toFormatSettingsResponse(analysisService.getFormatSettings(analysisId));
  }
```

Semantics note: Jackson 3 binds an absent key to `null` (skip → leave unchanged), explicit `null` to `Optional.empty()` (clear), and a string to `Optional.of(...)` (overwrite).

- [ ] **Step 5: Compile**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/anki/dto/FormatSettingsResponse.java src/main/java/com/felixkroemer/smort/application/anki/dto/UpdateFormatSettingsRequest.java src/main/java/com/felixkroemer/smort/application/anki/mapping/AnalysisRestMapper.java src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java
git commit -m "feat: add settings endpoints for analysis"
```

---

### Task 4: End-to-end verification

**Files:**
- None (verification only).

- [ ] **Step 1: Run the full build incl. the context-load test**

Run: `./mvnw test`
Expected: BUILD SUCCESS, 1 test passes (`SmortApplicationTests.contextLoads`).

- [ ] **Step 2: Start local DynamoDB**

Run: `docker compose up -d`
Expected: container `dynamodb-local` running on `:8000`. (If the `common-table` table was already set up for local dev, reuse it; otherwise create it via `aws --endpoint-url http://localhost:8000 dynamodb create-table --table-name common-table --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=sk,AttributeType=S --billing-mode PAY_PER_REQUEST`.)

- [ ] **Step 3: Run the app with the local profile**

Run (from repo root, in a separate terminal):

```bash
OPENAI_API_KEY=dummy BASE_DATA_DIR=.smort ANALYSIS_DB_DIRECTORY_NAME=anki/db ANALYSIS_MAX_DB_SIZE=52428800 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Expected: app starts on port 8080 without errors.

- [ ] **Step 4: Exercise the endpoints with curl**

Run the following and check each response:

```bash
# 4a. Create an analysis
curl -s -X POST http://localhost:8080/analysis
#  -> {"id":"<analysisId>"} — capture <analysisId>

# 4b. GET settings before any set (key present, value null)
curl -s http://localhost:8080/analysis/<analysisId>/settings
#  -> {"formatInstructions":null}

# 4c. PATCH to set
curl -s -X PATCH -H "Content-Type: application/json" -d '{"formatInstructions":"format as a list of bullets"}' http://localhost:8080/analysis/<analysisId>/settings
#  -> {"formatInstructions":"format as a list of bullets"}

# 4d. GET reflects the persisted value
curl -s http://localhost:8080/analysis/<analysisId>/settings
#  -> {"formatInstructions":"format as a list of bullets"}

# 4e. PATCH null clears
curl -s -X PATCH -H "Content-Type: application/json" -d '{"formatInstructions":null}' http://localhost:8080/analysis/<analysisId>/settings
#  -> {"formatInstructions":null}

# 4f. PATCH empty body is a no-op
curl -s -X PATCH -H "Content-Type: application/json" -d '{}' http://localhost:8080/analysis/<analysisId>/settings
#  -> {"formatInstructions":null}

# 4g. Missing analysis -> 404
curl -s -o /dev/null -w "%{http_code}" -X PATCH -H "Content-Type: application/json" -d '{"formatInstructions":"x"}' http://localhost:8080/analysis/00000000-0000-0000-0000-000000000000/settings
#  -> 404
```

- [ ] **Step 5: Confirm branch state**

Run: `git status` and `git log --oneline -5`
Expected: working tree clean, last three commits are the three feature commits on `feat/analysis-format-settings`.

# Deck/Analysis Template-Mode Formatting Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let deck and analysis formatting settings choose between three modes — use the user's default template, use a specific template, or use a custom formatting string — instead of a single optional formatting string.

**Architecture:** Settings move from one `Optional<String> formatInstructions` to three independently-updatable fields (`formattingMode`, `templateId`, `formatInstructions`) stored on the meta entities and mirrored into the settings records. A `FormattingSettingsResolver` turns a settings object into the effective formatting string, resolving templates (with a throw on a deleted referenced template). Call sites that previously read `settings.formatInstructions()` now call the resolver.

**Tech Stack:** Java 17, Spring Boot, DynamoDB Enhanced Client, Lombok, MapStruct.

## Global Constraints

- No tests are written and no build is run (AGENTS.md / spec). Implementing subagents must NOT run `./mvnw compile`, `./mvnw test`, etc. Compilation is owned by the human; note "compilation skipped" in each task report.
- Current user is the hardcoded `"default"` (no auth). Never pass a userId from controllers.
- `FormattingMode { DEFAULT, TEMPLATE, CUSTOM }`. `DEFAULT` resolves to the user's default template id (from user settings); `TEMPLATE` resolves `templateId` (throws `NotFoundException` if the referenced user template was deleted); `CUSTOM` returns the `formatInstructions` string verbatim (empty string is the user's problem).
- The three stored settings are always non-null. In the update request, each field is a plain nullable type; a `null` field means "don't change"; a non-null value sets it.
- `SystemFormattingTemplate.fromId(String)` and `SystemFormattingTemplate.DEFAULT.getId()` already exist in `domain.user`.
- Existing custom `formatInstructions` rows are not migrated.
- Work on branch `feat/deck-analysis-template-settings` in the worktree `.worktrees/feat-deck-analysis-template-settings`; commit after each task.

---

### Task 1: `FormattingMode` enum

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/common/FormattingMode.java`

**Interfaces:**
- Produces: `enum FormattingMode { DEFAULT, TEMPLATE, CUSTOM }` in package `com.felixkroemer.smort.domain.common`.

- [ ] **Step 1: Create the enum**

```java
package com.felixkroemer.smort.domain.common;

public enum FormattingMode {
  DEFAULT,
  TEMPLATE,
  CUSTOM
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/common/FormattingMode.java
git commit -m "feat: add FormattingMode enum"
```

---

### Task 2: Three settings fields on the meta entities

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckMetaEntity.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaEntity.java`

**Interfaces:**
- Consumes: `FormattingMode` (Task 1), `SystemFormattingTemplate` (`domain.user`, already exists).
- Produces: `DeckMetaEntity`/`AnalysisMetaEntity` now expose `getFormattingMode()`, `getTemplateId()`, `getFormatInstructions()` (String) and setters, replacing the single `Optional<String> formatInstructions`.

- [ ] **Step 1: `DeckMetaEntity` — replace the field**

Replace the `OptionalStringConverter` + `Optional<String> formatInstructions` block with three plain fields. Change the imports (`OptionalStringConverter` and `java.util.Optional` become unused here; add `FormattingMode` and `SystemFormattingTemplate`).

```java
  private FormattingMode formattingMode = FormattingMode.DEFAULT;
  private String templateId = SystemFormattingTemplate.DEFAULT.getId();
  private String formatInstructions = "";
```

New imports:
```java
import com.felixkroemer.smort.domain.common.FormattingMode;
import com.felixkroemer.smort.domain.user.SystemFormattingTemplate;
```
Remove imports: `com.felixkroemer.smort.infrastructure.dynamodb.OptionalStringConverter`, `java.util.Optional`.

- [ ] **Step 2: `AnalysisMetaEntity` — replace the field**

Same replacement:
```java
  private FormattingMode formattingMode = FormattingMode.DEFAULT;
  private String templateId = SystemFormattingTemplate.DEFAULT.getId();
  private String formatInstructions = "";
```
New imports:
```java
import com.felixkroemer.smort.domain.common.FormattingMode;
import com.felixkroemer.smort.domain.user.SystemFormattingTemplate;
```
Remove imports: `com.felixkroemer.smort.infrastructure.dynamodb.OptionalStringConverter`, `java.util.Optional`. (Keep `java.util.UUID`, `java.time.Instant`.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DeckMetaEntity.java src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/anki/AnalysisMetaEntity.java
git commit -m "feat: store formattingMode, templateId and formatInstructions on meta entities"
```

---

### Task 3: Settings records, DTOs and REST mappers

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckSettings.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisSettings.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/dto/DeckSettingsResponse.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/dto/UpdateDeckSettingsRequest.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/dto/AnalysisSettingsResponse.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/dto/UpdateAnalysisSettingsRequest.java`
- (Rest mappers `DeckRestMapper`/`AnalysisRestMapper` need no change — MapStruct maps by name.)

**Interfaces:**
- Consumes: `FormattingMode` (Task 1).
- Produces:
  - `DeckSettings(FormattingMode formattingMode, String templateId, String formatInstructions)`
  - `AnalysisSettings(FormattingMode formattingMode, String templateId, String formatInstructions)`
  - `DeckSettingsResponse(FormattingMode formattingMode, String templateId, String formatInstructions)`
  - `AnalysisSettingsResponse(FormattingMode formattingMode, String templateId, String formatInstructions)`
  - `UpdateDeckSettingsRequest(FormattingMode formattingMode, String templateId, String formatInstructions)`
  - `UpdateAnalysisSettingsRequest(FormattingMode formattingMode, String templateId, String formatInstructions)`

- [ ] **Step 1: Settings records**

`DeckSettings.java`:
```java
package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record DeckSettings(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}
```

`AnalysisSettings.java`:
```java
package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record AnalysisSettings(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}
```

- [ ] **Step 2: Response DTOs**

`DeckSettingsResponse.java`:
```java
package com.felixkroemer.smort.application.deck.dto;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record DeckSettingsResponse(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}
```

`AnalysisSettingsResponse.java`:
```java
package com.felixkroemer.smort.application.anki.dto;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record AnalysisSettingsResponse(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}
```

- [ ] **Step 3: Update request DTOs**

`UpdateDeckSettingsRequest.java`:
```java
package com.felixkroemer.smort.application.deck.dto;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record UpdateDeckSettingsRequest(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}
```

`UpdateAnalysisSettingsRequest.java`:
```java
package com.felixkroemer.smort.application.anki.dto;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record UpdateAnalysisSettingsRequest(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}
```
Each field is nullable; a `null` value means "don't change".

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckSettings.java src/main/java/com/felixkroemer/smort/domain/anki/AnalysisSettings.java src/main/java/com/felixkroemer/smort/application/deck/dto/ src/main/java/com/felixkroemer/smort/application/anki/dto/
git commit -m "feat: add formattingMode, templateId and formatInstructions to settings DTOs"
```

---

### Task 4: Drop `formatInstructions` from the domain aggregates

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/Deck.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/Analysis.java`
- (Entity mappers `DeckEntityMapper`/`AnalysisEntityMapper` need no change — the removed target field simply stops being mapped.)

**Interfaces:**
- Consumes: nothing new.
- Produces: `Deck` and `Analysis` no longer have `formatInstructions`. (Their all-args constructors are not used anywhere.)

- [ ] **Step 1: Remove the field from `Deck`**

Remove the line:
```java
  private Optional<String> formatInstructions = Optional.empty();
```
(`java.util.Optional` import stays — `bulkFormat` and `draftNote` still use it.)

- [ ] **Step 2: Remove the field from `Analysis`**

Remove the line:
```java
  private Optional<String> formatInstructions = Optional.empty();
```
(`java.util.Optional` import stays — `bulkFormat` still uses it.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/Deck.java src/main/java/com/felixkroemer/smort/domain/anki/Analysis.java
git commit -m "refactor: remove formatInstructions from deck and analysis aggregates"
```

---

### Task 5: Deck/Analysis settings get/update services and controllers

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java` (get/update methods only)
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java` (get/update methods only)
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java`

**Interfaces:**
- Consumes: `FormattingMode`, `SystemFormattingTemplate`, settings/DTOs (Tasks 1–3).
- Produces:
  - `DeckService.getDeckSettings(UUID)` → `DeckSettings` (3 fields); `DeckService.updateDeckSettings(UUID, FormattingMode, String, String)` → `DeckSettings`.
  - `AnalysisService.getAnalysisSettings(UUID)` → `AnalysisSettings` (3 fields); `AnalysisService.updateAnalysisSettings(UUID, FormattingMode, String, String)` → `AnalysisSettings`.

- [ ] **Step 1: `DeckService` settings methods**

Replace `getDeckSettings` and `updateDeckSettings`:

```java
public DeckSettings getDeckSettings(UUID deckId) {
  var meta = getMeta(deckId);
  return new DeckSettings(meta.getFormattingMode(), meta.getTemplateId(), meta.getFormatInstructions());
}

public DeckSettings updateDeckSettings(
    UUID deckId,
    FormattingMode formattingMode,
    String templateId,
    String formatInstructions) {
  var deck = getMeta(deckId);
  if (formattingMode != null) deck.setFormattingMode(formattingMode);
  if (templateId != null) deck.setTemplateId(templateId);
  if (formatInstructions != null) deck.setFormatInstructions(formatInstructions);
  if (formattingMode != null || templateId != null || formatInstructions != null) {
    deckRepository.saveDeckMeta(deck);
  }
  return new DeckSettings(deck.getFormattingMode(), deck.getTemplateId(), deck.getFormatInstructions());
}
```

Add import: `com.felixkroemer.smort.domain.common.FormattingMode`. (`SystemFormattingTemplate` is not used in DeckService; do not add it. `java.util.Optional` is already imported.)

- [ ] **Step 2: `AnalysisService` settings methods**

Replace `getAnalysisSettings` and `updateAnalysisSettings`:

```java
public AnalysisSettings getAnalysisSettings(UUID analysisId) {
  var meta = getMeta(analysisId);
  return new AnalysisSettings(meta.getFormattingMode(), meta.getTemplateId(), meta.getFormatInstructions());
}

public AnalysisSettings updateAnalysisSettings(
    UUID analysisId,
    FormattingMode formattingMode,
    String templateId,
    String formatInstructions) {
  var analysis = getMeta(analysisId);
  if (formattingMode != null) analysis.setFormattingMode(formattingMode);
  if (templateId != null) analysis.setTemplateId(templateId);
  if (formatInstructions != null) analysis.setFormatInstructions(formatInstructions);
  if (formattingMode != null || templateId != null || formatInstructions != null) {
    analysis.setUpdatedAt(Instant.now());
    analysisMetaRepository.save(analysis);
  }
  return new AnalysisSettings(analysis.getFormattingMode(), analysis.getTemplateId(), analysis.getFormatInstructions());
}
```

Add import: `com.felixkroemer.smort.domain.common.FormattingMode`. (`java.util.Optional` is already imported.)

- [ ] **Step 3: `DeckController` update method**

```java
@PatchMapping("/{deckId}/settings")
public DeckSettingsResponse updateDeckSettings(
    @PathVariable("deckId") UUID deckId,
    @RequestBody UpdateDeckSettingsRequest updateDeckSettingsRequest) {
  return deckRestMapper.toDeckSettingsResponse(
      deckService.updateDeckSettings(
          deckId,
          updateDeckSettingsRequest.formattingMode(),
          updateDeckSettingsRequest.templateId(),
          updateDeckSettingsRequest.formatInstructions()));
}
```

- [ ] **Step 4: `AnalysisController` update method**

```java
@PatchMapping("/{analysisId}/settings")
public AnalysisSettingsResponse updateAnalysisSettings(
    @PathVariable("analysisId") UUID analysisId,
    @RequestBody UpdateAnalysisSettingsRequest updateAnalysisSettingsRequest) {
  return analysisRestMapper.toAnalysisSettingsResponse(
      analysisService.updateAnalysisSettings(
          analysisId,
          updateAnalysisSettingsRequest.formattingMode(),
          updateAnalysisSettingsRequest.templateId(),
          updateAnalysisSettingsRequest.formatInstructions()));
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java src/main/java/com/felixkroemer/smort/domain/anki/AnalysisService.java src/main/java/com/felixkroemer/smort/application/deck/DeckController.java src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java
git commit -m "feat: support three independent formatting settings fields in deck/analysis settings"
```

---

### Task 6: `FormattingSettingsResolver`

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/user/FormattingSettingsResolver.java`

**Interfaces:**
- Consumes: `DeckSettings`, `AnalysisSettings`, `FormattingMode`, `SystemFormattingTemplate`, `UserFormattingTemplateRepository`, `UserSettingsRepository` (`findByUserId`, `findByUserIdAndTemplateId`).
- Produces:
  - `String resolve(DeckSettings settings)`
  - `String resolve(AnalysisSettings settings)`

- [ ] **Step 1: Implement the resolver**

```java
package com.felixkroemer.smort.domain.user;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.anki.AnalysisSettings;
import com.felixkroemer.smort.domain.deck.DeckSettings;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FormattingSettingsResolver {

  private static final String CURRENT_USER = "default";

  private final UserFormattingTemplateRepository userFormattingTemplateRepository;
  private final UserSettingsRepository userSettingsRepository;

  public String resolve(DeckSettings settings) {
    return switch (settings.formattingMode()) {
      case DEFAULT -> resolveTemplateContent(getDefaultTemplateId());
      case TEMPLATE -> resolveTemplateContent(settings.templateId());
      case CUSTOM -> settings.formatInstructions();
    };
  }

  public String resolve(AnalysisSettings settings) {
    return switch (settings.formattingMode()) {
      case DEFAULT -> resolveTemplateContent(getDefaultTemplateId());
      case TEMPLATE -> resolveTemplateContent(settings.templateId());
      case CUSTOM -> settings.formatInstructions();
    };
  }

  private String getDefaultTemplateId() {
    return userSettingsRepository
        .findByUserId(CURRENT_USER)
        .map(UserSettingsEntity::getDefaultTemplateId)
        .orElseThrow(
            () -> new NotFoundException("Could not find user settings. userId={}", CURRENT_USER));
  }

  private String resolveTemplateContent(String templateId) {
    var systemTemplate = SystemFormattingTemplate.fromId(templateId);
    if (systemTemplate.isPresent()) {
      return systemTemplate.get().getContent();
    }
    return userFormattingTemplateRepository
        .findByUserIdAndTemplateId(CURRENT_USER, templateId)
        .map(UserFormattingTemplateEntity::getContent)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Referenced formatting template does not exist. Please choose a valid template. id={}",
                    templateId));
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/user/FormattingSettingsResolver.java
git commit -m "feat: add FormattingSettingsResolver for deck/analysis formatting modes"
```

---

### Task 7: Deck-side call-site integration

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java`

**Interfaces:**
- Consumes: `FormattingSettingsResolver` (Task 6).
- Produces: the three services now resolve formatting via `formattingSettingsResolver.resolve(...)` wrapped in `Optional.of(...)`.

- [ ] **Step 1: Inject the resolver into `DeckService` and update `chat`**

Add a field to `DeckService`:
```java
private final FormattingSettingsResolver formattingSettingsResolver;
```
Add import `com.felixkroemer.smort.domain.user.FormattingSettingsResolver`.

In `chat` (currently `var formatInstructions = getDeckSettings(deckId).formatInstructions();`), change to:
```java
var formatInstructions = Optional.of(formattingSettingsResolver.resolve(getDeckSettings(deckId)));
```

- [ ] **Step 2: Inject the resolver into `NoteService` and update both methods**

Add a field:
```java
private final FormattingSettingsResolver formattingSettingsResolver;
```
Add import `com.felixkroemer.smort.domain.user.FormattingSettingsResolver`.

In `formatNote` (currently `var formatInstructions = deckService.getDeckSettings(deckId).formatInstructions();`), change to:
```java
var formatInstructions = Optional.of(formattingSettingsResolver.resolve(deckService.getDeckSettings(deckId)));
```
Do the same in `chat`.

- [ ] **Step 3: Inject the resolver into `DeckBulkFormatService` and update**

Add a field:
```java
private final FormattingSettingsResolver formattingSettingsResolver;
```
Add import `com.felixkroemer.smort.domain.user.FormattingSettingsResolver`.

In `processNotes` (currently `var formatInstructions = deckService.getDeckSettings(job.getDeckId()).formatInstructions();`), change to:
```java
var formatInstructions = Optional.of(formattingSettingsResolver.resolve(deckService.getDeckSettings(job.getDeckId())));
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java
git commit -m "feat: resolve deck formatting settings through FormattingSettingsResolver"
```

---

### Task 8: Analysis-side call-site integration

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisBulkFormatService.java`

**Interfaces:**
- Consumes: `FormattingSettingsResolver` (Task 6).
- Produces: `AnkiNoteService`/`AnalysisBulkFormatService` resolve formatting via `formattingSettingsResolver.resolve(analysisService.getAnalysisSettings(...))` wrapped in `Optional.of(...)`, replacing use of `analysis.getFormatInstructions()`.

- [ ] **Step 1: Inject the resolver into `AnkiNoteService` and update `formatNote` and `chat`**

Add a field:
```java
private final FormattingSettingsResolver formattingSettingsResolver;
```
Add import `com.felixkroemer.smort.domain.user.FormattingSettingsResolver`.

In `formatNote` (currently `var analysis = analysisService.getAnalysis(analysisId);` then `analysis.getFormatInstructions(),`), remove the `analysis` fetch and resolve instead. Change:
```java
var analysis = analysisService.getAnalysis(analysisId);
```
to:
```java
var formatInstructions = Optional.of(formattingSettingsResolver.resolve(analysisService.getAnalysisSettings(analysisId)));
```
and change the `analysis.getFormatInstructions(),` argument to `formatInstructions,`.

In `chat` (currently `var formatInstructions = analysisService.getAnalysisSettings(analysisId).formatInstructions();`), change to:
```java
var formatInstructions = Optional.of(formattingSettingsResolver.resolve(analysisService.getAnalysisSettings(analysisId)));
```

- [ ] **Step 2: Inject the resolver into `AnalysisBulkFormatService` and update `processNotes`**

Add a field:
```java
private final FormattingSettingsResolver formattingSettingsResolver;
```
Add import `com.felixkroemer.smort.domain.user.FormattingSettingsResolver`.

In `processNotes`, replace the `analysis` fetch block:
```java
    var analysisId = job.getAnalysisId();
    Analysis analysis;
    try {
      analysis = analysisService.getAnalysis(analysisId);
    } catch (NotFoundException e) {
      throw e.withSeverity(LogSeverity.ERROR);
    }
```
with:
```java
    var analysisId = job.getAnalysisId();
    Optional<String> formatInstructions;
    try {
      formatInstructions =
          Optional.of(formattingSettingsResolver.resolve(analysisService.getAnalysisSettings(analysisId)));
    } catch (NotFoundException e) {
      throw e.withSeverity(LogSeverity.ERROR);
    }
```
and change the `analysis.getFormatInstructions(),` argument (inside `chatOrchestrationService.formatNote(...)`) to `formatInstructions,`.

If the `Analysis` type is now unused elsewhere in `AnalysisBulkFormatService`, remove its now-unused import. Leave `NotFoundException`/`LogSeverity` imports (still used by the catch).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java src/main/java/com/felixkroemer/smort/domain/anki/AnalysisBulkFormatService.java
git commit -m "feat: resolve analysis formatting settings through FormattingSettingsResolver"
```

---

## Self-Review Notes

- **Spec coverage:** `FormattingMode` enum (T1); entity storage (T2); settings records + DTOs (T3); aggregate mirror removal (T4); get/update endpoints with three independent optional fields (T5); resolver with DEFAULT/TEMPLATE/CUSTOM semantics and throw on deleted template (T6); all call sites routed through the resolver — deck (T7) and analysis, including the two formerly aggregate-based sites `AnkiNoteService.formatNote` and `AnalysisBulkFormatService` (T8).
- **Type consistency:** `FormattingMode` and the three-field settings records are used consistently. `resolve(DeckSettings)`/`resolve(AnalysisSettings)` return `String`. The "`null` = don't change" semantics are implemented via the `!= null` guards in T5.
- **No placeholders:** every code step contains full code.
- **Unused imports:** `OptionalStringConverter` stays (used by `AbstractChatMessageEntity`); removed only from the two meta entities. Check for and remove any now-unused imports flagged in each task (noted in T2 and T8).
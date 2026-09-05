# Design: User-defined formatting templates + default template selection

Date: 2026-09-05

## Goal

Allow a user to define their own named formatting templates in their global
settings and to select which template is their default. This is the first step
of a larger feature (later steps will let deck/analysis settings choose between
a template and a custom formatting string). This step is **user-level only**:
it adds storage of user formatting templates plus the selection of a default
template, exposed through a user-settings REST resource. Deck/analysis settings
are untouched in this step.

System templates are predefined and uneditable. The default template id is
never null and initially points at a system template. If the user later points
the default at one of their own templates and then deletes that template, the
fallback to the main system template is **lazy** — it happens only when the
default is actually resolved/consumed (deferred to the follow-up step).

## Background facts

- There is no auth/security context. The current user is the hardcoded dummy
  user `"default"`, exactly as decks/analyses already use.
- There is no frontend in this repo (pure REST/JSON backend).
- The only existing "settings" are a single `Optional<String> formatInstructions`
  per deck/analysis, persisted on `DeckMetaEntity`/`AnalysisMetaEntity`, with
  trivial GET/PATCH endpoints and MapStruct mappers.
- `ChatUtil.formattingRules()` returns the built-in default formatting-rule
  block — this is the content of the main system template.

## Approach

Compose a `UserSettings` aggregate that bundles the scalar default-template
setting together with the user's formatting templates, loaded together and
mapped transparently — exactly mirroring how `BulkFormat` is composed into the
`Analysis`/`Deck` aggregates. System templates are represented as a Java enum
(constant ids, uneditable) and merged into the aggregate at load time so that a
single serialization of the settings domain object exposes everything the
frontend needs (including the SYSTEM/USER distinction).

Storage follows the existing single `common-table` pattern: one shared
partition key per user (`USER#<userId>`) with distinct sort keys for the
settings item (`SETTINGS#`) and each template item (`TEMPLATE#<id>`).

## Data model

### System templates (enum, constant ids, uneditable)

```java
public enum SystemFormattingTemplate {
  DEFAULT("DEFAULT", "Default", /* content = the built-in formatting rules */);
  private final String id;
  private final String name;
  private final String content;
}
```

`DEFAULT` is the "main system template" and its content corresponds to the
current built-in rules (`ChatUtil.formattingRules()`). The enum is structured
so more members can be added later; only `DEFAULT` ships in this step.

### Domain

```java
public class UserSettings {
  private String defaultTemplateId;          // never null; defaults to DEFAULT id
  private List<FormattingTemplate> templates = List.of();  // system + user, with source
}

public record FormattingTemplate(String id, String name, String content, TemplateSource source) {}

public enum TemplateSource { SYSTEM, USER }
```

The domain aggregate holds the system templates too (with `source = SYSTEM`) so
the whole thing can be serialized in one pass.

## Persistence (single `common-table`)

- `UserSettingsEntity`:
  - `pk` = `UserKeys.userPk(userId)` = `USER#<userId>`
  - `sk` = `UserSettingsKeys.settingsSk()` = `SETTINGS#`
  - `String defaultTemplateId` — set to the main system template id in the
    constructor (never null).
- `UserFormattingTemplateEntity`:
  - `pk` = `USER#<userId>`
  - `sk` = `UserSettingsKeys.templateSk(templateId)` = `TEMPLATE#<templateId>`
  - `templateId`, `name`, `content`.

New key helpers:
- `infrastructure/dynamodb/keys/partition/UserKeys.java` — `userPk(String userId)`.
- `infrastructure/dynamodb/keys/sort/UserSettingsKeys.java` —
  `settingsSk()`, `templateSk(String templateId)`, `templatePrefix()`.

New repositories (backed by `common-table` beans in `DynamoDbClientConfig`):
- `UserSettingsRepository` — `findByUserId(userId)` (single `getItem` by
  `USER#<id>` / `SETTINGS#`), `save(entity)`.
- `UserFormattingTemplateRepository` — `findByUserId(userId)` (query by
  partition key with `sortBeginsWith(TEMPLATE#)`, mirroring
  `DeckRepository.findNotesByDeckId`), `findByUserIdAndTemplateId(userId, id)`
  (single `getItem`), `save(entity)`, `delete(userId, id)`.

New table beans: `userSettingsTable`, `userFormattingTemplateTable` in
`DynamoDbClientConfig`.

## Service layer (`domain/user/UserSettingsService.java`)

Composes the aggregate on every load, mirroring `AnalysisService.getAnalysis`:

```java
public UserSettings getUserSettings() {
  var settings = userSettingsRepository
      .findByUserId("default")
      .orElseGet(() -> new UserSettingsEntity("default"));
  var userTemplates = userFormattingTemplateRepository.findByUserId("default").stream()
      .map(formattingTemplateEntityMapper::toFormattingTemplate)   // source = USER
      .toList();
  var systemTemplates = Arrays.stream(SystemFormattingTemplate.values())
      .map(s -> new FormattingTemplate(s.id(), s.name(), s.content(), TemplateSource.SYSTEM))
      .toList();
  return userSettingsEntityMapper.toUserSettings(
      settings, Stream.concat(systemTemplates.stream(), userTemplates.stream()).toList());
}
```

Operations:
- `updateUserSettings(Optional<String> defaultTemplateId)` — same `!= null`
  guard as `DeckService.updateDeckSettings`; persists to the settings entity
  (creating it if absent). No strict validation of the referenced id (lazy
  fallback is handled by the future consumer).
- `createTemplate(String name, String content)` — generate a `UUID`, save a
  `UserFormattingTemplateEntity`, return `FormattingTemplate(source=USER)`.
- `updateTemplate(String id, String name, String content)` — 404 via
  `NotFoundException` if not found/owned.
- `deleteTemplate(String id)` — 404 via `NotFoundException` if not found/owned;
  does **not** touch the default-template reference (lazy fallback).

Mappers:
- `UserSettingsEntityMapper` (aggregate, MapStruct):
  `UserSettings toUserSettings(UserSettingsEntity settings, List<FormattingTemplate> templates)`
  — mirrors `DeckEntityMapper.toDeck(meta, bulkFormat, draftNote)`.
- `FormattingTemplateEntityMapper` (per-item): a default method mapping
  `UserFormattingTemplateEntity` → `FormattingTemplate` with `source = USER` —
  mirrors `BulkFormatEntityMapper`.

## REST API (`application/user/UserSettingsController.java`, base `/user/settings`)

- `GET /user/settings` → `UserSettingsResponse` (serializes the whole
  aggregate — no separate listing endpoint).
- `PATCH /user/settings` → body `UpdateUserSettingsRequest(defaultTemplateId)`
  (null body = don't change) → `UserSettingsResponse`.
- `POST /user/settings/templates` → body `{name, content}` → created
  `FormattingTemplateResponse`.
- `PUT /user/settings/templates/{id}` → body `{name, content}` → updated
  `FormattingTemplateResponse`; 404 if not owned.
- `DELETE /user/settings/templates/{id}` → `204`; 404 if not owned.

DTOs (`application/user/dto/`):
- `UserSettingsResponse(String defaultTemplateId, List<FormattingTemplateResponse> templates)`
- `FormattingTemplateResponse(String id, String name, String content, TemplateSource type)`
  — the `type` lets the frontend tell SYSTEM (uneditable) from USER templates.
- `UpdateUserSettingsRequest(Optional<String> defaultTemplateId)`
- `CreateFormattingTemplateRequest(String name, String content)`
- `UpdateFormattingTemplateRequest(String name, String content)`

Rest mapper (`UserSettingsRestMapper`, MapStruct): `UserSettings` →
`UserSettingsResponse`; `FormattingTemplate` → `FormattingTemplateResponse`
with `type` mapped from `source`. System templates are rejected from
POST/PUT/DELETE by construction (they live only in the enum, not as items).

## Data flow

`GET /user/settings` → `UserSettingsService.getUserSettings()` → reads the
settings item, queries the user's template items, merges system templates →
`UserSettingsEntityMapper` composes the aggregate → `UserSettingsRestMapper`
serializes it (defaultTemplateId + templates each with type).

## Error handling

Template CRUD uses the existing `NotFoundException` (→ 404 via
`GlobalExceptionHandler`). No other new failure modes.

## Testing

No new tests (per AGENTS.md, tests are only written when explicitly requested).
Compilation is owned by the human and skipped in implementation.

## Operational note

No new DynamoDB GSI is required — template listing uses a sort-key-prefix query
within the `USER#<userId>` partition. The existing `common-table` is reused.
The lazy default-template fallback resolution is intentionally deferred to the
follow-up step that actually consumes templates for formatting; this step only
stores the raw `defaultTemplateId` and the templates.
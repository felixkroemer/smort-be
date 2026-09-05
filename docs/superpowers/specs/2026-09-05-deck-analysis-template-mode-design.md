# Design: Deck/Analysis template-mode formatting settings

Date: 2026-09-05

## Goal

Deck and analysis formatting settings currently hold a single
`Optional<String> formatInstructions`. This changes so the settings decide
between three formatting modes: use the user's default template, use a
specific template, or use a custom formatting string. The three settings are
stored independently so the update endpoint can change just one of them.

## Background

- Deck settings live on `DeckSettings` / `DeckMetaEntity` as
  `Optional<String> formatInstructions`; analysis on `AnalysisSettings` /
  `AnalysisMetaEntity` identically.
- The user-level templates feature already exists: `SystemFormattingTemplate`
  enum (constant ids, uneditable), user templates persisted per user, and a
  user-level `defaultTemplateId` in `UserSettings` that is never null and
  points at a valid template (initially a system template). Deleting a user
  template that is the current user default is blocked.
- `ChatUtil.formatInstructions(Optional<String>)` builds the LLM prompt,
  using the custom instructions if present else the system DEFAULT template
  content.
- Current user is the hardcoded dummy `"default"` (no auth).

## Formatting mode

```java
public enum FormattingMode { DEFAULT, TEMPLATE, CUSTOM }
```

- `DEFAULT` — use the user's default template (from user settings
  `defaultTemplateId`). Always resolvable, since the default cannot be
  deleted.
- `TEMPLATE` — use the template selected by `templateId`. If that template is
  a user template that was later deleted, resolution throws an exception
  telling the user to choose a valid template (no lazy fallback).
- `CUSTOM` — use the `formatInstructions` string. An empty string is
  honored literally (the user's own problem), i.e. no formatting override.

`DEFAULT` is the default mode for new/unset decks and analyses. Existing rows
that only have a `formatInstructions` are not migrated; with mode `DEFAULT`
they use the user's default template and ignore their old string.

## Data model

Entities (`DeckMetaEntity`, `AnalysisMetaEntity`) replace
`Optional<String> formatInstructions` with three fields:
- `FormattingMode formattingMode` — default `DEFAULT`
- `String templateId` — default `SystemFormattingTemplate.DEFAULT.getId()`
- `String formatInstructions` — default `""`

The `OptionalStringConverter` becomes unused for these fields. Domain records:
```java
public record DeckSettings(FormattingMode formattingMode, String templateId, String formatInstructions) {}
public record AnalysisSettings(FormattingMode formattingMode, String templateId, String formatInstructions) {}
```

The three settings remain stored directly on the meta entities for now (the
existing pattern). Moving settings to dedicated storage is a future feature
and out of scope here.

## Update endpoint — independent fields

`UpdateDeckSettingsRequest` / `UpdateAnalysisSettingsRequest`:
```java
public record UpdateDeckSettingsRequest(
    Optional<FormattingMode> formattingMode,
    Optional<String> templateId,
    Optional<String> formatInstructions) {}
```
Each field is optional; a `null` field means "don't change" (existing `!= null`
guard pattern). Only the fields present in the body are applied and persisted.
Because all three stored settings are always non-null, `Optional.empty()` and
`null` currently have the same effect ("don't change") — there is no
clear/unset operation today. The `Optional` shape is kept so a future setting
that can be explicitly unset can express that via `Optional.empty()`. Response
records return the full three-field settings.

## Resolution — `FormattingSettingsResolver`

A component in `domain.user` (it resolves templates) with both
`UserFormattingTemplateRepository` and `UserSettingsRepository` injected:

```java
public String resolve(DeckSettings s) {
  return switch (s.formattingMode()) {
    case DEFAULT  -> resolveTemplateContent(getDefaultTemplateId());
    case TEMPLATE -> resolveTemplateContent(s.templateId());
    case CUSTOM   -> s.formatInstructions();
  };
}
```

- `getDefaultTemplateId()` returns the current user's `defaultTemplateId`,
  throwing `NotFoundException` if no settings row exists (cannot happen at
  format time).
- `resolveTemplateContent(String id)` returns the content of a system
  template (by id) or an existing user template (by id); otherwise throws
  `NotFoundException` with a message telling the user to choose a valid
  template. In `DEFAULT` mode this never throws (default is always valid); in
  `TEMPLATE` mode it throws when the referenced user template was deleted.

An `AnalysisSettings` overload resolves the same way.

## Call-site integration

Call sites that currently read `settings.formatInstructions()` switch to
`resolver.resolve(settings)`:
- `DeckService.chat`
- `NoteService` (deck note format/chat)
- `DeckBulkFormatService`
- `AnkiNoteService` (analysis formatting)

The resolved string is passed downstream to `ChatUtil.formatInstructions(...)`
as before.

## Error handling

- `TEMPLATE` mode referencing a deleted user template → `NotFoundException`
  (404) with a message to choose a valid template.
- Missing user settings during resolution → `NotFoundException`.

## Testing

No new tests (per AGENTS.md, tests are only written when explicitly requested).
Compilation is owned by the human and skipped in implementation.
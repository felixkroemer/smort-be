# Chat Package Split Design

Date: 2026-10-03

## Goal

Split the monolithic `com.felixkroemer.smort.domain.chat` package into two focused
packages: one for orchestration (persistence/coordination) and one for the actual
OpenAI chat. Pure reorganization — no behavior or logic changes.

In addition, rename the `*ChatMessage` domain model classes to `*ChatResponse`:
`ChatMessage` -> `ChatResponse`, `TextChatMessage` -> `TextChatResponse`,
`StoreNoteToolChatMessage` -> `StoreNoteToolChatResponse`,
`DraftNoteToolChatMessage` -> `DraftNoteToolChatResponse`.
`ChatMessageMeta` and the infra `ChatMessageEntity` are NOT renamed.

## Target structure

### `domain.chat` — shared domain model (stays in root)
- `ChatResponse` (was `ChatMessage`) + `TextChatResponse` (was `TextChatMessage`) /
  `StoreNoteToolChatResponse` (was `StoreNoteToolChatMessage`) /
  `DraftNoteToolChatResponse` (was `DraftNoteToolChatMessage`)
- `ChatMessageMeta`
- `ChatContext` + `NoteChatContext` / `DeckChatContext`
- `ChatUtil`
- `ToolCallHandler`

### `domain.chat.orchestration` — persistence / coordination
- `ChatOrchestrationService`
- `UserActionContextService`

### `domain.chat.llm` — the actual OpenAI chat
- `NoteChatService`
- `DeckChatService`
- `NoteChatTools`
- `DeckChatTools`
- `NoteChatToolType`
- `DeckChatToolType`

## Dependency direction

- `domain.chat.llm` -> `domain.chat` (root model)
- `domain.chat.orchestration` -> `domain.chat.llm` + `domain.chat` (root model)
- No cycles.

## Rationale

- The root holds the types both layers depend on, so dependency direction is clean.
- `ChatOrchestrationService` (DDB storage, transactional writes, tool-effect application)
  is decoupled from the OpenAI plumbing.
- `UserActionContextService` reads `ChatRepository`, so it is grouped with orchestration.

## Required changes

1. Move files into `orchestration` (2) and `llm` (4), updating each `package` declaration.
2. Add imports for root model types in the moved files (they were same-package before).
3. Rename the `*ChatMessage` domain classes to `*ChatResponse` (interface + 3 concrete
   records), and update all `implements` clauses, the `ToolCallHandler.execute` parameter
   type, and every reference across `domain` and `application`. `ChatMessageMeta` and the
   infra `ChatMessageEntity` keep their names.
4. Update external consumers that reference moved classes:
   - `ChatOrchestrationService` (used by `NoteService`, `DeckService`, `AnkiNoteService`,
     bulk-format services, controllers).
   - Tool-type enums / tool services used by `NoteService`, `DeckService`, `AnkiNoteService`,
     `DeckBulkFormatService`, `AnalysisBulkFormatService`, `AnalysisController`, `DeckController`.
   - Root types (`ChatMessage`, `ToolCallHandler`, contexts) remain importable from `domain.chat`.

## Scope

Pure package reorganization. Near-duplicate methods (`handleStoreNoteToolResponse` /
`handleDraftNoteToolResponse`, the repeated "extract single output item" blocks, the
`acknowledge*ToolCall` pair) are intentionally NOT combined in this change. Combining them
is a separate decision.
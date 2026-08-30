# Chat User Action Context Design

Date: 2026-08-30

## Overview

When a new chat message is sent to the model, collect the latest consecutive
run of user-initiated chat messages (`userInitiated == true`) and inject them
as JSON context into the model instructions, so the model knows e.g. that the
user manually formatted a note.

This applies at the 3 points where chat messages are sent:

1. `NoteChatService.formatNote` (manual note format)
2. `NoteChatService.chat` (note chat)
3. `DeckChatService.chat` (deck chat)

The tool-acknowledgement model calls (`acknowledgeStoreNoteToolCall`,
`acknowledgeDraftNoteToolCall`) do NOT receive this context.

## Components

### `UserActionContextService` (new, `domain/chat`)

- `Optional<String> buildContext(String pk, T entityId)`
- Queries `chatRepository.findAll(pk, entityId)` (already returns newest-first
  by `createdAt`).
- Walks from the newest message while `isUserInitiated()` is true, stopping at
  the first non-user-initiated message. This yields the **latest consecutive
  run** of user-initiated messages.
- Reverses the run to chronological order.
- Builds a JSON array of entries, one per kept message that has a `toolName`
  (user-initiated messages without a `toolName` contribute nothing):
  `{"toolName": "...", "arguments": {...}}`.
- Serializes the array with the existing `ObjectMapper`.
- Returns `Optional.empty()` when there is no run. Otherwise returns the
  section text:

  ```
  Recent user actions:
  [{"toolName": "STORE_NOTE", "arguments": {"front": "...", "back": "..."}}]
  ```

### `ChatUtil` (modified)

- Add `appendUserActions(String instructions, Optional<String> context)`:
  returns `instructions + "\n\n" + context` when present, otherwise
  `instructions` unchanged.

### `ChatOrchestrationService` (modified)

`formatNote`, `noteChat`, and `deckChat` each call
`userActionContextService.buildContext(pk, entityId)` once and pass the result
into the corresponding chat service call as a new parameter:

- `formatNote` -> `noteChatService.formatNote(content, formatInstructions, userActionContext)`
- `noteChat` -> `noteChatService.chat(ctx, message, formatInstructions, previousResponseId, userActionContext)`
- `deckChat` -> `deckChatService.chat(ctx, message, formatInstructions, previousResponseId, userActionContext)`

### Chat services (modified)

- `NoteChatService.formatNote(fields, formatInstructions, userActionContext)`:
  instructions = `ChatUtil.appendUserActions(ChatUtil.formatInstructions(formatInstructions), userActionContext)`.
- `NoteChatService.chat(ctx, message, formatInstructions, previousResponseId, userActionContext)`:
  instructions = `ChatUtil.appendUserActions(CHAT_INSTRUCTIONS.formatted(fieldsBlock, <format instructions>), userActionContext)`.
- `DeckChatService.chat(ctx, message, formatInstructions, previousResponseId, userActionContext)`:
  instructions = `ChatUtil.appendUserActions(CHAT_INSTRUCTIONS.formatted(<deck context sections>), userActionContext)`.

No changes to the existing instruction templates.

## Data flow

1. A `ChatOrchestrationService` entry point calls `buildContext(pk, entityId)`.
2. The resulting `Optional<String>` is passed to the chat service.
3. The chat service builds its instructions and appends the context section
   when present.
4. The model receives the user actions as JSON in its instructions.

## Error handling

DynamoDB query exceptions propagate as today. No special handling is added.

## Testing

No tests are written (per AGENTS.md: tests only when explicitly requested).

## Out of scope

- Tool-acknowledgement model calls receive no context.
- User-initiated text messages (no `toolName`) are part of the consecutive run
  but render nothing in the JSON.
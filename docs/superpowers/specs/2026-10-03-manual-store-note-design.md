# Manual Store Note — Design

Date: 2026-10-03

## Overview

Users can edit a note's `front` and `back` in the frontend and save it via an
endpoint. Today only the LLM can change a note's content (via the `STORE_NOTE`
tool) or the user can trigger a format. This adds a direct user-initiated save.

The save persists the note and appends a user-initiated `STORE_NOTE` tool-call
message to the note's chat history, so the LLM sees it as a recent user action
via `UserActionContextService`. No LLM call is made.

Both deck notes and analysis (derived) notes are covered, mirroring how the
existing `/format` endpoints exist in both controllers/services.

## Endpoints

```
PATCH /decks/{deckId}/notes/{noteId}
PATCH /analysis/{analysisId}/notes/{noteId}
```

Body (both):

```
UpdateNoteRequest(String front, String back)
```

Responses:

- Deck: updated `NoteResponse`.
- Analysis: updated `DerivedNoteResponse`.

`UpdateNoteRequest` is a shared record placed in a new
`application/note/dto` package, parallel to how `ChatMessageRequest` is shared
in `application/chat/dto`. This avoids coupling `application/anki` to
`application/deck` DTOs.

## Chat message representation

The user-initiated message mirrors the message produced by manual formatting:

- `type = TOOL_CALL`
- `toolName = STORE_NOTE`
- `arguments = {front, back}`
- `userInitiated = true`
- `message = empty`
- `callId = ""`
- `previousResponseId = empty`
- `responseId = UUID.randomUUID().toString()`

A synthetic `responseId` is safe because user-initiated messages live under the
`CHAT#U#` sort-key prefix, which `ChatRepository.findLatestChatMessage` (the
source of the OpenAI `previousResponseId` chain) ignores. No synthetic id is
ever sent to OpenAI.

`lastFormattedAt` is left unchanged: a manual edit is not a format.

## Components

1. `UpdateNoteRequest(String front, String back)` — shared record,
   `application/note/dto`.

2. `ChatOrchestrationService.storeNote(pk, entityId, front, back,
   toolHandlers)` — new method alongside `formatNote`, without an LLM call:
   - Build `ChatMessageEntity.toolCall(pk, entityId, empty, UUID, empty, "",
     STORE_NOTE, empty, true, {front, back})`.
   - `chatRepository.saveInTx` the message, `applyToolEffect(...)` (reused),
     then one `transactWriteItems`.
   - Return `List.of(messageEntity)`.

3. `NoteService.updateNote(deckId, noteId, front, back)`:
   - Load note via `findNoteByDeckIdAndNoteId`; `NotFoundException` if missing.
   - Tool handler (`StoreNoteToolChatMessage`): set `front`/`back` on the loaded
     entity (leave `lastFormattedAt`), `deckRepository.saveNoteInTx`.
   - Call `chatOrchestrationService.storeNote(DeckKeys.deckPk(deckId), noteId,
     front, back, handlers)`.
   - Return the updated `NoteEntity`; the controller maps to `NoteResponse`.

4. `AnkiNoteService.updateNote(analysisId, noteId, front, back)`:
   - Verify the base note exists. `AnkiNoteRepository.findNoteByAnalysisIdAndNoteId`
     uses JPA `getSingleResult()`, which throws `NoResultException` when absent;
     translate that to `NotFoundException` (404) in the service.
   - Tool handler (`StoreNoteToolChatMessage`): upsert the derived note —
     update the existing one (preserving its `lastFormattedAt`) or create via
     the `DerivedNoteEntity(analysisId, noteId, front, back)` constructor
     (empty `lastFormattedAt`; `derivedNoteEntityMapper` would stamp
     `Instant.now()`), then `derivedNoteRepository.saveInTx`.
   - Call `chatOrchestrationService.storeNote(AnalysisKeys.analysisPk(analysisId),
     noteId, front, back, handlers)`.
   - Return the updated `DerivedNoteEntity`; the controller maps to
     `DerivedNoteResponse`.

5. Controllers add the two `PATCH` endpoints, delegating to the services and
   mapping the result.

## Data flow

Controller → service → orchestration → one `TransactWriteItems` containing the
note put plus the user-initiated chat-message put. All-or-nothing: there is
never a chat message without the note change, or vice versa.

## Error handling

- Note (or analysis base note) not found → `NotFoundException` (404).
- `front` or `back` null → `SmortException` (400). Empty strings are allowed.
- Saving identical content is still persisted and still appends the
  user-initiated message; the user explicitly initiated the action.

## Infrastructure

No Terraform changes. Reuses the existing `common-table` and chat item shape; no
new attributes or indexes.

## Files touched

- New: `application/note/dto/UpdateNoteRequest.java`
- `domain/chat/ChatOrchestrationService.java` — add `storeNote`
- `domain/deck/NoteService.java` — add `updateNote`
- `domain/anki/AnkiNoteService.java` — add `updateNote`
- `application/deck/DeckController.java` — add `PATCH` endpoint
- `application/anki/AnalysisController.java` — add `PATCH` endpoint

## Testing

No tests are written unless explicitly requested (per AGENTS.md). Compilation is
skipped during implementation per AGENTS.md and verified later by the human.

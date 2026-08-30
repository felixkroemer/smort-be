# Confirm and Add Draft Note Design

Date: 2026-08-31

## Overview

Deck chat can draft a new note (`DraftNote` tool) but the user cannot yet
confirm and add that drafted note to the deck. This feature adds a dedicated
REST endpoint that, on user confirmation, adds the drafted note to the deck,
clears the draft, stores a user-generated chat message for the store-event,
and returns it to the user.

The stored message mirrors the manual-format pattern: a synthetic tool-call
chat message with `userInitiated = true`, so it is persisted and returned to
the user and also flows into `UserActionContextService` for later chats.

## Components

### `DeckController` (modify)

New endpoint:

```
POST /decks/{deckId}/draft-note/store
```

Calls `deckService.storeDraftNote(deckId)` and returns
`List<ChatMessageResponse>` via `ChatMessageRestMapper` (mirrors
`PATCH /decks/{deckId}/notes/{noteId}/format`).

### `DeckService.storeDraftNote(UUID deckId)` (new)

1. Load the draft via `draftNoteRepository.findDraftNote(deckId)`; throw
   `NotFoundException` if none exists.
2. Build the new note:
   `noteEntityMapper.toNoteEntity(deckId, UUID.randomUUID(), new NoteSchema(draft.getFront(), draft.getBack()))`.
3. Build the synthetic ADD_NOTE chat message:

   ```java
   ChatMessageEntity.toolCall(
       DeckKeys.deckPk(deckId),
       deckId,
       Optional.empty(),                    // message
       UUID.randomUUID().toString(),        // responseId — never used for the OpenAI chain
       Optional.empty(),                    // previousResponseId
       UUID.randomUUID().toString(),        // callId — no real function call behind it
       DeckChatToolType.ADD_NOTE.name(),
       Optional.empty(),                    // response
       true,                                // userInitiated
       Map.of("front", draft.getFront(), "back", draft.getBack()));
   ```

   The `responseId` is a generated UUID. It does not need to be a real OpenAI
   response id because `ChatMessageEntity` sorts user-initiated messages under
   the `CHAT#U#` prefix (`ChatKeys.chatMessageSk`) and
   `ChatRepository.findLatestChatMessage` queries only the `CHAT#C#` prefix,
   so the ADD_NOTE message is never returned as the chaining
   `previousResponseId`.
4. One DynamoDB transaction
   (`TransactWriteItemsEnhancedRequest` + `enhancedClient.transactWriteItems`):
   - save the new note (`deckRepository.saveNoteInTx`),
   - delete the draft (`draftNoteRepository.deleteInTx`),
   - save the chat message (`chatRepository.saveInTx`).
5. Return `List.of(addNoteMessageEntity)`.

### `DeckChatToolType` (modify)

Add `ADD_NOTE(DeckChatTools.AddNoteTool.class)`.

### `DeckChatTools` (modify)

Add a placeholder parser class `AddNoteTool { public String front; public String back; }`.
It is NOT registered as an OpenAI tool (only `DraftNoteTool` is added in
`DeckChatService.chat`), so the model never calls it; it exists only to give
the `ADD_NOTE` enum constant a parser class.

### `DraftNoteRepository` (modify)

Add `deleteInTx(TransactWriteItemsEnhancedRequest.Builder txBuilder, UUID deckId)`
using `txBuilder.addDeleteItem(draftNoteTable, Key.builder().partitionValue(DeckKeys.deckPk(deckId)).sortValue(DraftNoteKeys.draftNoteSk()).build())`.

## Data flow

1. User confirms the draft in the UI -> `POST /decks/{deckId}/draft-note/store`.
2. `DeckService.storeDraftNote` loads the draft (404 if absent), builds the
   note and the synthetic ADD_NOTE message, and commits all three writes
   atomically.
3. The stored message is returned to the user as `List<ChatMessageResponse>`.
4. The draft is gone; the note appears in the deck's note list.
5. Later deck chats include the new note in the "deck currently contains
   these notes" section, and the ADD_NOTE event is included in the
   user-action context (`UserActionContextService`) because it is
   `userInitiated = true`.

## Error handling

- `NotFoundException` when no draft exists -> HTTP 404.
- The DynamoDB transaction is atomic: if any write fails, all three roll back.

## Testing

No tests are written (per AGENTS.md: tests only when explicitly requested).

## Out of scope

- No model call or acknowledgement message for the store-event (it is
  user-initiated, like the manual-format `STORE_NOTE` message).
- No chat tool for the model to trigger the add — a dedicated endpoint only.
- `DeckChatService` tool registration is unchanged (`DraftNoteTool` only).
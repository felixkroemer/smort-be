# Deck Chat Draft Note Design

## Goal

Let the deck chat draft a new note based on the conversation. When the user asks the
chat to draft a note about a topic, the model invokes a `DraftNote` tool with a `front`
and `back`. The drafted note reflects prior discussion and any clarifications the user
asked for, while staying concise. The draft is persisted as its own DynamoDB entity —
one per imported deck. Reading it is exposed via a dedicated endpoint, analogous to how
bulk format status is read. Creating an actual note from the draft, clearing the draft,
and user edits to it are out of scope.

## Scope

In scope:
- DraftNote tool callable from the deck chat, returning front/back.
- Persist the draft (single entity per deck, overwritten on each draft).
- Follow formatting rules in the main chat, mirroring the note chat.
- Read the draft via a dedicated endpoint (404 when absent).
- Delete the draft entity when the deck is cleaned up by `DeckCron`.

Out of scope:
- Creating an actual note from the draft.
- Clearing the draft on demand.
- User edits to the draft.
- Read endpoint that returns the draft as part of the deck list.

## Approach

Mirror the existing StoreNote pattern for the deck chat, using deck-specific
counterparts (`DeckChatTools`, `DeckChatToolType`, `DraftNoteToolChatMessage`) and the
tool-handler dispatch by message class that the recent chat refactor established.

## Changes

### 1. Chat tooling (domain layer)

**`DeckChatTools`** (new, mirrors `NoteChatTools`):

```java
public class DeckChatTools {
    @JsonClassDescription("Draft a new ankiNote for the deck.")
    static class DraftNoteTool {
        public String front;
        public String back;
    }
}
```

**`DeckChatToolType`** (new, mirrors `NoteChatToolType`):
`DRAFT_NOTE(DeckChatTools.DraftNoteTool.class)` with the same `fromToolName(...)`
lookup that maps the tool class simple name to the enum value.

**`DraftNoteToolChatMessage`** (new record, mirrors `StoreNoteToolChatMessage`):
`(String callId, String front, String back, ChatMessageMeta meta)` implementing
`ChatMessage`. Add it to the `ChatMessage` sealed interface permits list.

**`DeckChatService`**:
- `chat(...)` gains `.addTool(DeckChatTools.DraftNoteTool.class)`. When the output
  item is a function call, resolve the tool type via `DeckChatToolType.fromToolName`
  and, for `DRAFT_NOTE`, parse the arguments and return a `DraftNoteToolChatMessage`.
  Text responses behave as today. The single-output-item constraint is kept.
- New `acknowledgeDraftNoteToolCall(String callId, String previousResponseId)`
  mirroring `NoteChatService.acknowledgeStoreNoteToolCall`: sends the
  `{callId: "ok"}` function-call output back to the model and returns the resulting
  text message.

**`ChatOrchestrationService`**:
- `deckChat(...)`: handle `DraftNoteToolChatMessage` via a new
  `handleDraftNoteToolResponse(...)` mirroring `handleStoreNoteToolResponse`:
  persist the tool-call chat message (userInitiated=false) and the ack text message,
  and apply the registered tool effect, all in one transaction.
- `noteChat(...)`: the switch is now non-exhaustive with the new permitted type; add
  a `default -> throw` branch (a `DraftNoteToolChatMessage` can never be returned by
  the note chat).
- `applyToolEffect` is reused unchanged.

**`ChatMessageEntity.toolCall(...)`**: generalize the `NoteChatToolType` parameter to a
plain `String toolName`. Callers pass the enum's `.name()` (`STORE_NOTE`,
`DRAFT_NOTE`).

### 2. Instructions / formatting context

The deck chat system instructions template (`DeckChatService.CHAT_INSTRUCTIONS`) is
extended to include draft guidance and the shared formatting rules:

```
Your task is to assist the user in learning about and improving their Anki deck.
You can discuss the deck's content, help identify gaps, and suggest improvements.

When the user asks you to draft a new note for the deck, use the DraftNote tool.
The "front" should be the question or term, the "back" the answer or explanation.
Take the conversation into account: if the topic was discussed before, or the user
asked for clarifications or adjustments, reflect that in the note — but keep it
concise, not overly verbose.

For the formatting, consider these rules:
%s

The deck currently contains these notes:
%s
```

- First `%s`: `ChatUtil.formattingRules()` (reused verbatim: plain markdown, no HTML,
  HTML→markdown conversion, fix spelling). The note-only "concatenate fields with
  headings" rule stays in the note chat.
- Second `%s`: the existing notes list from `DeckChatContext`.
- `acknowledgeDraftNoteToolCall` uses the same instructions; the notes list is not
  needed there and is passed empty.
- `ChatUtil` helpers are otherwise untouched.

### 3. Persistence (infrastructure layer)

**`DraftNoteEntity`** (new, in `infrastructure/dynamodb/deck/`):
- PK `DECK#<deckId>` (same partition as deck meta, notes, and chat).
- SK `META#DRAFTNOTE#` — fixed sort key, so exactly one draft per deck; a new draft
  overwrites the previous one.
- Fields: `pk`, `sk`, `front`, `back`.

**`DraftNoteKeys`** (new, in `keys/sort/`): `draftNoteSk()` returning
`"META#DRAFTNOTE#"`.

**`DraftNoteRepository`** (new):
- `saveInTx(TransactWriteItemsEnhancedRequest.Builder txBuilder, DraftNoteEntity entity)`
- `findDraftNote(UUID deckId)` returning `Optional<DraftNoteEntity>`
- `delete(UUID deckId)`

**`DynamoDbClientConfig`**: add a `DynamoDbTable<DraftNoteEntity>` bean on
`common-table` (mirrors the other entity beans).

**`DeckService.chat(...)`**: the currently-empty tool handler map becomes a handler
for `DraftNoteToolChatMessage` that saves a new `DraftNoteEntity(deckId, front, back)`
into the transaction.

**`DeckCron.deleteDecksMarkedForDeletion()`**: also call
`draftNoteRepository.delete(deckId)` alongside `deleteDeckNotes`/`deleteDeckMeta`.

### 4. Read draft note (application layer)

- `DeckService.getDraftNote(UUID deckId)` reads via `findDraftNote` and throws
  `NotFoundException` when absent (mirrors `DeckBulkFormatService.getJobStatus`).
- `DeckController`: `GET /{deckId}/draft-note` returning `DraftNoteResponse(front, back)`
  (404 when no draft exists).
- `DraftNoteResponse` (new DTO) and a dedicated `DraftNoteRestMapper` mapping
  `DraftNoteEntity` → `DraftNoteResponse` (mirrors how `BulkFormatRestMapper` is its own
  mapper).

## DynamoDB Data Model

- PK: `DECK#<deckId>`
- SK: `META#DRAFTNOTE#`
- Attributes: `front`, `back` (plus `pk`/`sk`)

## Error Handling

- Multiple output items in a deck chat turn → `SmortException` (kept from today).
- Unknown tool name → `DeckChatToolType.fromToolName` throws `SmortException`.
- `acknowledgeDraftNoteToolCall` must yield a `TextChatMessage`, else `SmortException`
  (mirrors `handleStoreNoteToolResponse`).
- No handler registered for `DraftNoteToolChatMessage` → `applyToolEffect` throws.
- Repeated drafts overwrite the single draft row (fixed SK).

## Testing

No tests. Per AGENTS.md, tests are only written when explicitly requested. Compilation
is owned by the human and verified later.

## Files

| File | Change |
|------|--------|
| `domain/chat/DeckChatTools.java` | New — `DraftNoteTool` |
| `domain/chat/DeckChatToolType.java` | New — `DRAFT_NOTE` enum |
| `domain/chat/DraftNoteToolChatMessage.java` | New — record |
| `domain/chat/ChatMessage.java` | Add `DraftNoteToolChatMessage` to permits |
| `domain/chat/DeckChatService.java` | Add tool, tool-call handling, ack method, extended instructions |
| `domain/chat/ChatOrchestrationService.java` | Dispatch `DraftNoteToolChatMessage` in `deckChat`, `default` in `noteChat` |
| `infrastructure/dynamodb/chat/ChatMessageEntity.java` | `toolCall(...)` param `NoteChatToolType` → `String toolName` |
| `domain/chat/ChatUtil.java` | Unchanged (reuse `formattingRules()`) |
| `infrastructure/dynamodb/deck/DraftNoteEntity.java` | New |
| `infrastructure/dynamodb/keys/sort/DraftNoteKeys.java` | New |
| `infrastructure/dynamodb/deck/DraftNoteRepository.java` | New |
| `infrastructure/dynamodb/DynamoDbClientConfig.java` | Add `DraftNoteEntity` table bean |
| `domain/deck/DeckService.java` | Register draft tool handler in `chat()`, add `getDraftNote()` |
| `domain/cron/DeckCron.java` | Delete draft in cleanup |
| `application/deck/DeckController.java` | Add `GET /{deckId}/draft-note` |
| `application/deck/dto/DraftNoteResponse.java` | New |
| `application/deck/mapping/DraftNoteRestMapper.java` | New — maps `DraftNoteEntity` → `DraftNoteResponse` |
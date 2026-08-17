# Deck Chat Infrastructure Design

## Goal

Add chat infrastructure for Decks (imported decks) similar to what exists for Notes. This enables future features like passing note titles to the LLM to find gaps. For now, the focus is on infrastructure only - deck chat will be plain text chat without tool calls.

## Changes

### 1. Rename `noteId` to `entityId`

Rename the `noteId` parameter/field to `entityId` across the chat infrastructure:

- `ChatKeys.chatMessageSk(noteId, ...)` → `chatMessageSk(entityId, ...)`
- `ChatKeys.llmChatMessagesPrefix(noteId)` → `llmChatMessagesPrefix(entityId)`
- `ChatKeys.userChatMessagesPrefix(noteId)` → `userChatMessagesPrefix(entityId)`
- `ChatOrchestrationService` method parameters: `noteId` → `entityId`
- `ChatMessageEntity` field: `noteId` → `entityId`
- `ChatRepository` method parameters: `noteId` → `entityId`

This is a pure rename with no behavior change.

### 2. Add `ChatContext` sealed interface

Create a sealed interface with two permitted record types to represent different chat contexts:

```java
public sealed interface ChatContext permits NoteChatContext, DeckChatContext {}

public record NoteChatContext(UUID noteId, Map<String, String> fields) implements ChatContext {}
public record DeckChatContext(UUID deckId, String deckName) implements ChatContext {}
```

### 3. Split `ChatOrchestrationService.chat()` into two methods

Replace the single `chat()` method with two type-safe methods:

```java
// For note chat - includes storeNoteHandler for saving note updates
public List<ChatMessageEntity> noteChat(
    String pk, NoteChatContext ctx, String message,
    TriConsumer<TransactWriteItemsEnhancedRequest.Builder, String, String> storeNoteHandler)

// For deck chat - no storeNoteHandler needed
public List<ChatMessageEntity> deckChat(
    String pk, DeckChatContext ctx, String message)
```

The `noteChat()` method reuses the existing `formatNote()` logic. The `deckChat()` method calls `chatService.chat(DeckChatContext, message, previousResponseId)`.

### 4. Split `ChatService.chat()` into overloaded methods

```java
// For note chat - includes StoreNoteTool
public ChatMessage chat(NoteChatContext ctx, String message, Optional<String> previousResponseId)

// For deck chat - no tools
public ChatMessage chat(DeckChatContext ctx, String message, Optional<String> previousResponseId)
```

### 5. Add deck chat methods to `DeckService`

```java
public List<ChatMessageEntity> chat(UUID deckId, String message) {
    var deck = deckRepository.findDeckMetaByDeckId(deckId)
        .orElseThrow(() -> new NotFoundException("Could not find deck. deckId={}", deckId));
    
    var ctx = new DeckChatContext(deckId, deck.getName());
    return chatOrchestrationService.deckChat(DeckKeys.deckPk(deckId), ctx, message);
}

public List<ChatMessageEntity> getChat(UUID deckId) {
    return chatOrchestrationService.getChat(DeckKeys.deckPk(deckId), deckId);
}
```

### 6. Add deck chat endpoints to `DeckController`

```java
@PostMapping("/{deckId}/chat")
public List<ChatMessageResponse> postDeckChatMessage(
    @PathVariable("deckId") UUID deckId,
    @RequestBody ChatMessageRequest chatMessageRequest) {
    var chatMessageResponses = deckService.chat(deckId, chatMessageRequest.message());
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
}

@GetMapping("/{deckId}/chat")
public List<ChatMessageResponse> getDeckChat(
    @PathVariable("deckId") UUID deckId) {
    var chatMessageResponses = chatOrchestrationService.getChat(DeckKeys.deckPk(deckId), deckId);
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
}
```

## DynamoDB Data Model

Deck chat messages share the same partition as deck notes:

- PK: `DECK#<deckId>` (same as note chat)
- SK: `CHAT#U#<entityId>#<createdAt>#<responseId>` (entityId = deckId for deck chat, noteId for note chat)

This means deck chat and note chat coexist in the same partition, distinguished by the entityId in the sort key.

## Files to Modify

| File | Change |
|------|--------|
| `ChatKeys.java` | Rename `noteId` → `entityId` |
| `ChatMessageEntity.java` | Rename `noteId` field → `entityId` |
| `ChatRepository.java` | Rename `noteId` params → `entityId` |
| `ChatOrchestrationService.java` | Rename params, add `noteChat()` and `deckChat()` methods |
| `ChatService.java` | Add overloaded `chat()` methods for `NoteChatContext` and `DeckChatContext` |
| `DeckService.java` | Add `chat()` and `getChat()` methods |
| `DeckController.java` | Add deck chat endpoints |
| `NoteService.java` | Update to use `NoteChatContext` |
| `AnkiNoteService.java` | Update to use `NoteChatContext` |
| `ChatContext.java` | New file - sealed interface |
| `NoteChatContext.java` | New file - record |
| `DeckChatContext.java` | New file - record |

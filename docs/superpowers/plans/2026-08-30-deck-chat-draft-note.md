# Deck Chat Draft Note Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the deck chat draft a new note (front/back) via a `DraftNote` tool call, persist it as a single per-deck DynamoDB entity, and expose it through a dedicated read endpoint.

**Architecture:** Mirror the existing note-chat StoreNote pattern with deck-specific counterparts (`DeckChatTools.DraftNoteTool`, `DeckChatToolType`, `DraftNoteToolChatMessage` in the sealed `ChatMessage` hierarchy). `DeckChatService` gains the tool, tool-call handling, and an acknowledgment call. `ChatOrchestrationService.deckChat()` dispatches the new message type via the existing `toolHandlers` map. The draft is persisted to a new `DraftNoteEntity` (PK `DECK#<deckId>`, SK `META#DRAFTNOTE#`).

**Tech Stack:** Java (Spring Boot), DynamoDB Enhanced Client, OpenAI Responses API, Lombok, MapStruct

## Global Constraints

- Branch: `feat/deck-chat-draft-note` (already created and checked out)
- Do NOT run build/compile commands (`./mvnw compile`, `./mvnw test`, etc.). The human owns compilation and verifies it later.
- Write NO tests. Tests are only written when explicitly requested. Each task ends with a commit instead of a test cycle.
- Follow existing code conventions: Lombok (`@Getter`/`@Setter`/`@NoArgsConstructor`), MapStruct mappers with `unmappedTargetPolicy = ReportingPolicy.ERROR`, Java text blocks for instructions, DynamoDB Enhanced `@DynamoDbBean` entities with annotated `pk`/`sk`.
- All DynamoDB entities live on the `common-table`.

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `domain/chat/DeckChatTools.java` | Create | `DraftNoteTool` parser class |
| `domain/chat/DeckChatToolType.java` | Create | `DRAFT_NOTE` enum |
| `domain/chat/DraftNoteToolChatMessage.java` | Create | Record for the draft tool call |
| `domain/chat/ChatMessage.java` | Modify | Permit `DraftNoteToolChatMessage` |
| `domain/chat/DeckChatService.java` | Modify | Add tool, tool-call handling, ack method, extended instructions |
| `domain/chat/ChatOrchestrationService.java` | Modify | Dispatch `DraftNoteToolChatMessage` in `deckChat`, `default` in `noteChat`, `String toolName` callers |
| `infrastructure/dynamodb/chat/ChatMessageEntity.java` | Modify | `toolCall(...)` param `NoteChatToolType` → `String toolName` |
| `infrastructure/dynamodb/deck/DraftNoteEntity.java` | Create | Draft persistence entity |
| `infrastructure/dynamodb/keys/sort/DraftNoteKeys.java` | Create | `META#DRAFTNOTE#` sort key |
| `infrastructure/dynamodb/deck/DraftNoteRepository.java` | Create | save/find/delete for the draft |
| `infrastructure/dynamodb/DynamoDbClientConfig.java` | Modify | Add `DraftNoteEntity` table bean |
| `domain/cron/DeckCron.java` | Modify | Delete draft in deck cleanup |
| `domain/deck/DeckService.java` | Modify | Register draft tool handler in `chat()`, add `getDraftNote()` |
| `application/deck/DeckController.java` | Modify | Add `GET /{deckId}/draft-note` |
| `application/deck/dto/DraftNoteResponse.java` | Create | Read DTO |
| `application/deck/mapping/DraftNoteRestMapper.java` | Create | Entity → DTO mapping |

---

### Task 1: New deck chat tooling types

Creates the three deck-chat tool types and adds `DraftNoteToolChatMessage` to the sealed `ChatMessage` hierarchy. Also makes the existing `noteChat` switch exhaustive again (a `DraftNoteToolChatMessage` can never be returned by the note chat).

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatTools.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatToolType.java`
- Create: `src/main/java/com/felixkroemer/smort/domain/chat/DraftNoteToolChatMessage.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatMessage.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java:93-99`

**Interfaces:**
- Consumes: `ChatMessage` sealed interface (existing), `SmortException` (existing), `ChatMessageMeta` (existing)
- Produces: `DeckChatTools.DraftNoteTool` (public `front`, `back` fields), `DeckChatToolType.DRAFT_NOTE` + `DeckChatToolType.fromToolName(String)`, `DraftNoteToolChatMessage(String callId, String front, String back, ChatMessageMeta meta)` implementing `ChatMessage`

- [ ] **Step 1: Create `DeckChatTools.java`**

```java
package com.felixkroemer.smort.domain.chat;

import com.fasterxml.jackson.annotation.JsonClassDescription;

public class DeckChatTools {

    @JsonClassDescription("Draft a new ankiNote for the deck.")
    static class DraftNoteTool {
        public String front;
        public String back;
    }
}
```

- [ ] **Step 2: Create `DeckChatToolType.java`**

Mirrors `NoteChatToolType` exactly (same `@RequiredArgsConstructor`, same package-private `fromToolName` lookup):

```java
package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.common.exception.SmortException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum DeckChatToolType {
  DRAFT_NOTE(DeckChatTools.DraftNoteTool.class);
  private final Class<?> parserClass;

  static DeckChatToolType fromToolName(String name) {
    for (DeckChatToolType type : values()) {
      if (type.parserClass.getSimpleName().equals(name)) {
        return type;
      }
    }
    throw new SmortException("Unexpected tool called. toolName={}", name);
  }
}
```

- [ ] **Step 3: Create `DraftNoteToolChatMessage.java`**

```java
package com.felixkroemer.smort.domain.chat;

public record DraftNoteToolChatMessage(
    String callId, String front, String back, ChatMessageMeta meta) implements ChatMessage {}
```

- [ ] **Step 4: Modify `ChatMessage.java` to permit the new type**

Replace the permits list:

```java
public sealed interface ChatMessage
    permits TextChatMessage, StoreNoteToolChatMessage, DraftNoteToolChatMessage {}
```

- [ ] **Step 5: Add a `default` branch to the `noteChat` switch in `ChatOrchestrationService.java`**

The sealed interface now has three permitted types, so the previously-exhaustive switch in `noteChat()` (around line 93) needs a default branch. Find:

```java
    return switch (chatMessage) {
      case TextChatMessage r ->
          handleChatMessageTextResponse(pk, ctx.noteId(), message, r, latestChatMessageResponseId);
      case StoreNoteToolChatMessage r ->
          handleStoreNoteToolResponse(
              pk, ctx.noteId(), message, r, latestChatMessageResponseId, toolHandlers);
    };
```

Replace with:

```java
    return switch (chatMessage) {
      case TextChatMessage r ->
          handleChatMessageTextResponse(pk, ctx.noteId(), message, r, latestChatMessageResponseId);
      case StoreNoteToolChatMessage r ->
          handleStoreNoteToolResponse(
              pk, ctx.noteId(), message, r, latestChatMessageResponseId, toolHandlers);
      default -> throw new SmortException("Unexpected message type received");
    };
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/DeckChatTools.java \
       src/main/java/com/felixkroemer/smort/domain/chat/DeckChatToolType.java \
       src/main/java/com/felixkroemer/smort/domain/chat/DraftNoteToolChatMessage.java \
       src/main/java/com/felixkroemer/smort/domain/chat/ChatMessage.java \
       src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java
git commit -m "feat: add deck chat draft note tool types"
```

---

### Task 2: Extend DeckChatService with the DraftNote tool and ack

Adds the tool to the deck chat request, handles the function-call output, extends the system instructions with the shared formatting rules, and adds the acknowledgment call.

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java`

**Interfaces:**
- Consumes: `DeckChatTools.DraftNoteTool`, `DeckChatToolType.DRAFT_NOTE`/`fromToolName`, `DraftNoteToolChatMessage` (all from Task 1), `ChatUtil.formattingRules()` (existing), `ChatUtil.getResponseOutputText` (existing), `OpenAIClient`/`ResponseCreateParams` (existing usage)
- Produces: `DeckChatService.chat(DeckChatContext, String, Optional<String>)` now returns `TextChatMessage` or `DraftNoteToolChatMessage`; `DeckChatService.acknowledgeDraftNoteToolCall(String callId, String previousResponseId)` returning `ChatMessage`

- [ ] **Step 1: Extend the `CHAT_INSTRUCTIONS` constant**

Replace the existing `CHAT_INSTRUCTIONS` block:

```java
  private static final String CHAT_INSTRUCTIONS =
      """
      Your task is to assist the user in learning about and improving their Anki deck.
      You can discuss the deck's content, help identify gaps, and suggest improvements.

      When the user asks you to draft a new note for the deck, use the DraftNote tool.
      The "front" should be the question or term, the "back" the answer or explanation.
      Take the conversation into account: if the topic was discussed before, or the user asked
      for clarifications or adjustments, reflect that in the note, but keep it concise,
      not overly verbose.

      For the formatting, consider these rules:
      %s

      The deck currently contains these notes:
      %s
      """;
```

- [ ] **Step 2: Update `chat()` to pass the formatting rules and notes, and register the tool**

Replace the `params` build and the output-handling block in `chat()`:

```java
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(
                CHAT_INSTRUCTIONS.formatted(
                    ChatUtil.formattingRules(), String.join("\n", ctx.notes())))
            .input(fullInput)
            .previousResponseId(previousResponseId)
            .model(model)
            .addTool(DeckChatTools.DraftNoteTool.class)
            .build();
```

Keep the existing single-output-item reduction. Replace the trailing `if (responseOutputItem.isMessage()) ... else throw` block with a version that also handles function calls:

```java
    if (responseOutputItem.isFunctionCall()) {
      var responseFunctionToolCall = responseOutputItem.asFunctionCall();
      var toolType = DeckChatToolType.fromToolName(responseFunctionToolCall.name());
      switch (toolType) {
        case DRAFT_NOTE -> {
          var draftNoteToolCall =
              responseFunctionToolCall.arguments(DeckChatTools.DraftNoteTool.class);
          return new DraftNoteToolChatMessage(
              responseFunctionToolCall.callId(),
              draftNoteToolCall.front,
              draftNoteToolCall.back,
              meta);
        }
        default ->
            throw new SmortException(
                "Unexpected tool called. toolName={}", responseFunctionToolCall.name());
      }
    } else if (responseOutputItem.isMessage()) {
      ResponseOutputText outputText =
          ChatUtil.getResponseOutputText(responseOutputItem.asMessage());
      return new TextChatMessage(outputText.text(), meta);
    } else {
      throw new SmortException("Unexpected response output item type");
    }
```

- [ ] **Step 3: Add `acknowledgeDraftNoteToolCall`**

Add this method (mirrors `NoteChatService.acknowledgeStoreNoteToolCall`; the second `%s` for the notes list is passed empty here):

```java
  public ChatMessage acknowledgeDraftNoteToolCall(String callId, String previousResponseId) {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS.formatted(ChatUtil.formattingRules(), ""))
            .input(
                ResponseCreateParams.Input.ofResponse(
                    List.of(
                        ResponseInputItem.ofFunctionCallOutput(
                            ResponseInputItem.FunctionCallOutput.builder()
                                .callId(callId)
                                .outputAsJson("ok")
                                .build()))))
            .previousResponseId(previousResponseId)
            .model(model)
            .build();

    var response = openAIClient.responses().create(params);
    var output = response.output();

    var responseOutputItem =
        output.stream()
            .reduce(
                (a, b) -> {
                  throw new SmortException("Received multiple output items");
                })
            .orElseThrow(() -> new SmortException("Received no output items"));

    var meta = new ChatMessageMeta(response.id(), response.previousResponseId(), Instant.now());
    ResponseOutputText outputText = ChatUtil.getResponseOutputText(responseOutputItem.asMessage());
    return new TextChatMessage(outputText.text(), meta);
  }
```

`List` is not yet imported in this file — add `import java.util.List;` to the imports (the file currently imports only `java.time.Instant` and `java.util.Optional`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java
git commit -m "feat: add DraftNote tool and ack to deck chat"
```

---

### Task 3: Dispatch the draft tool message in the orchestrator

Handles `DraftNoteToolChatMessage` in `deckChat()`, adds the mirror handler, and generalizes `ChatMessageEntity.toolCall(...)` to take a `String` tool name so both note and deck tool calls persist correctly.

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/chat/ChatMessageEntity.java`

**Interfaces:**
- Consumes: `DraftNoteToolChatMessage` (Task 1), `DeckChatService.acknowledgeDraftNoteToolCall` (Task 2), `DeckChatToolType.DRAFT_NOTE` (Task 1), `ToolCallHandler` map (existing)
- Produces: `ChatOrchestrationService.deckChat(String pk, DeckChatContext ctx, String message, Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers)` returns the tool-call + ack chat message entities and applies the registered tool effect; `ChatMessageEntity.toolCall(...)` now takes a `String toolName`

- [ ] **Step 1: Generalize `ChatMessageEntity.toolCall(...)`**

In `ChatMessageEntity.java`, remove the now-unused import `com.felixkroemer.smort.domain.chat.NoteChatToolType`. Change the `toolCall` factory parameter from `NoteChatToolType noteChatTool` to `String toolName` and the body from `Optional.of(noteChatTool.name())` to `Optional.of(toolName)`.

- [ ] **Step 2: Update the two existing `toolCall(...)` callers in `ChatOrchestrationService.java`**

Both call sites (in `formatNote` around line 69 and `handleStoreNoteToolResponse` around line 138) pass `NoteChatToolType.STORE_NOTE`. Change both to `NoteChatToolType.STORE_NOTE.name()`.

- [ ] **Step 3: Dispatch `DraftNoteToolChatMessage` in `deckChat()`**

Replace the `deckChat()` switch (it currently has a `TextChatMessage` case plus a `default` that throws):

```java
    var chatMessage = deckChatService.chat(ctx, message, latestChatMessageResponseId);

    return switch (chatMessage) {
      case TextChatMessage r ->
          handleChatMessageTextResponse(pk, ctx.deckId(), message, r, latestChatMessageResponseId);
      case DraftNoteToolChatMessage r ->
          handleDraftNoteToolResponse(
              pk, ctx.deckId(), message, r, latestChatMessageResponseId, toolHandlers);
      default -> throw new SmortException("Unexpected message type received");
    };
```

- [ ] **Step 4: Add `handleDraftNoteToolResponse`**

Add this private method (mirrors `handleStoreNoteToolResponse`; note `DeckChatToolType.DRAFT_NOTE.name()`):

```java
  private @NonNull <T> List<ChatMessageEntity> handleDraftNoteToolResponse(
      String pk,
      T entityId,
      String message,
      DraftNoteToolChatMessage draftNoteToolChatMessageResponse,
      Optional<String> latestChatMessageResponseId,
      Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers) {
    var toolCallChatMessageEntity =
        ChatMessageEntity.toolCall(
            pk,
            entityId,
            message,
            draftNoteToolChatMessageResponse.meta().responseId(),
            latestChatMessageResponseId,
            draftNoteToolChatMessageResponse.callId(),
            DeckChatToolType.DRAFT_NOTE.name(),
            Optional.empty(),
            false);
    var ackResponse =
        deckChatService.acknowledgeDraftNoteToolCall(
            draftNoteToolChatMessageResponse.callId(),
            draftNoteToolChatMessageResponse.meta().responseId());
    if (ackResponse instanceof TextChatMessage(String text, ChatMessageMeta meta)) {
      var chatMessageEntity =
          ChatMessageEntity.text(
              pk, entityId, Optional.empty(), meta.responseId(), latestChatMessageResponseId, text);
      var txBuilder = TransactWriteItemsEnhancedRequest.builder();
      chatRepository.saveInTx(txBuilder, toolCallChatMessageEntity);
      chatRepository.saveInTx(txBuilder, chatMessageEntity);
      applyToolEffect(txBuilder, draftNoteToolChatMessageResponse, toolHandlers);
      enhancedClient.transactWriteItems(txBuilder.build());
      return List.of(toolCallChatMessageEntity, chatMessageEntity);
    } else {
      throw new SmortException("Expected ChatMessageTextResponse in response to tool call ack.");
    }
  }
```

`@NonNull`, `ChatMessageMeta`, `DeckChatToolType`, and `DraftNoteToolChatMessage` are all already imported or in the same package.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java \
       src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/chat/ChatMessageEntity.java
git commit -m "feat: dispatch draft note tool call in deck chat orchestration"
```

---

### Task 4: Draft note persistence

Creates the DynamoDB entity, sort-key helper, repository, table bean, and the deck-deletion cleanup.

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DraftNoteEntity.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/DraftNoteKeys.java`
- Create: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DraftNoteRepository.java`
- Modify: `src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/cron/DeckCron.java`

**Interfaces:**
- Consumes: `DeckKeys.deckPk(UUID)` (existing), `DeckRepository` (existing)
- Produces: `DraftNoteEntity(UUID deckId, String front, String back)` with getters for `pk`, `sk`, `front`, `back`; `DraftNoteKeys.draftNoteSk()` returning `"META#DRAFTNOTE#"`; `DraftNoteRepository.saveInTx(TransactWriteItemsEnhancedRequest.Builder, DraftNoteEntity)`, `DraftNoteRepository.findDraftNote(UUID) -> Optional<DraftNoteEntity>`, `DraftNoteRepository.delete(UUID)`; a `DraftNoteEntity` table bean named `draftNoteTable`

- [ ] **Step 1: Create `DraftNoteKeys.java`**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

public final class DraftNoteKeys {

  public static String draftNoteSk() {
    return "META#DRAFTNOTE#";
  }
}
```

- [ ] **Step 2: Create `DraftNoteEntity.java`**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.DraftNoteKeys;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class DraftNoteEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private String front;
  private String back;

  public DraftNoteEntity(UUID deckId, String front, String back) {
    this.pk = DeckKeys.deckPk(deckId);
    this.sk = DraftNoteKeys.draftNoteSk();
    this.front = front;
    this.back = back;
  }
}
```

- [ ] **Step 3: Create `DraftNoteRepository.java`**

```java
package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.DraftNoteKeys;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@Repository
@RequiredArgsConstructor
public class DraftNoteRepository {

  private final DynamoDbTable<DraftNoteEntity> draftNoteTable;

  public void saveInTx(
      TransactWriteItemsEnhancedRequest.Builder txBuilder, DraftNoteEntity entity) {
    txBuilder.addPutItem(draftNoteTable, entity);
  }

  public Optional<DraftNoteEntity> findDraftNote(UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(DraftNoteKeys.draftNoteSk())
            .build();
    return Optional.ofNullable(draftNoteTable.getItem(key));
  }

  public void delete(UUID deckId) {
    var key =
        Key.builder()
            .partitionValue(DeckKeys.deckPk(deckId))
            .sortValue(DraftNoteKeys.draftNoteSk())
            .build();
    draftNoteTable.deleteItem(key);
  }
}
```

- [ ] **Step 4: Add the table bean in `DynamoDbClientConfig.java`**

Add the import `com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteEntity` and this bean (alongside the other entity beans, e.g. after `noteTable`):

```java
  @Bean
  public DynamoDbTable<DraftNoteEntity> draftNoteTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(DraftNoteEntity.class));
  }
```

- [ ] **Step 5: Delete the draft in `DeckCron.deleteDecksMarkedForDeletion()`**

Add the `DraftNoteRepository` field to the class (`private final DraftNoteRepository draftNoteRepository;` — the class is `@RequiredArgsConstructor`). Inside the `for` loop, between `deleteDeckNotes` and `deleteDeckMeta`:

```java
        deckRepository.deleteDeckNotes(deckMeta.getDeckId());
        draftNoteRepository.delete(deckMeta.getDeckId());
        deckRepository.deleteDeckMeta(deckMeta.getDeckId());
```

Add the import `com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteRepository`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DraftNoteEntity.java \
       src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/keys/sort/DraftNoteKeys.java \
       src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/deck/DraftNoteRepository.java \
       src/main/java/com/felixkroemer/smort/infrastructure/dynamodb/DynamoDbClientConfig.java \
       src/main/java/com/felixkroemer/smort/domain/cron/DeckCron.java
git commit -m "feat: add draft note persistence entity and repository"
```

---

### Task 5: Register the draft tool handler and add `getDraftNote` in DeckService

Wires the draft tool call to persistence and adds the read method.

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`

**Interfaces:**
- Consumes: `ChatOrchestrationService.deckChat` (Task 3), `DraftNoteToolChatMessage` (Task 1), `DraftNoteEntity`/`DraftNoteRepository` (Task 4), `ToolCallHandler` (existing)
- Produces: `DeckService.chat(UUID deckId, String message)` persists a `DraftNoteEntity` when the model drafts; `DeckService.getDraftNote(UUID deckId) -> DraftNoteEntity` (throws `NotFoundException` when absent)

- [ ] **Step 1: Register the draft tool handler in `chat()`**

Add imports for `com.felixkroemer.smort.domain.chat.ChatMessage`, `com.felixkroemer.smort.domain.chat.DraftNoteToolChatMessage`, `com.felixkroemer.smort.domain.chat.ToolCallHandler`, `com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteEntity`, `com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteRepository`, and `java.util.Map`.

Add the `DraftNoteRepository` field to the class (`private final DraftNoteRepository draftNoteRepository;` — the class is `@RequiredArgsConstructor`).

Replace the current `chat()` method body so the empty tool-handler map becomes:

```java
  public List<ChatMessageEntity> chat(UUID deckId, String message) {
    var deck =
        deckRepository
            .findDeckMetaByDeckId(deckId)
            .orElseThrow(() -> new NotFoundException("Could not find deck. deckId={}", deckId));

    var notes = deckRepository.findNotesByDeckId(deckId).stream().map(NoteEntity::getFront).toList();

    var ctx = new DeckChatContext(deckId, deck.getName(), notes);

    Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
        Map.of(
            DraftNoteToolChatMessage.class,
            (tx, toolCall) -> {
              var m = (DraftNoteToolChatMessage) toolCall;
              draftNoteRepository.saveInTx(
                  tx, new DraftNoteEntity(deckId, m.front(), m.back()));
            });

    return chatOrchestrationService.deckChat(DeckKeys.deckPk(deckId), ctx, message, toolHandlers);
  }
```

- [ ] **Step 2: Add `getDraftNote`**

```java
  public DraftNoteEntity getDraftNote(UUID deckId) {
    return draftNoteRepository
        .findDraftNote(deckId)
        .orElseThrow(() -> new NotFoundException("Could not find draft note. deckId={}", deckId));
  }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: persist draft note tool calls and read draft in deck service"
```

---

### Task 6: Expose the draft note read endpoint

Adds the DTO, mapper, and REST endpoint for reading the draft (404 when absent), mirroring the bulk format status endpoint pattern.

**Files:**
- Create: `src/main/java/com/felixkroemer/smort/application/deck/dto/DraftNoteResponse.java`
- Create: `src/main/java/com/felixkroemer/smort/application/deck/mapping/DraftNoteRestMapper.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`

**Interfaces:**
- Consumes: `DeckService.getDraftNote(UUID)` (Task 5), `DraftNoteEntity` (Task 4)
- Produces: `GET /decks/{deckId}/draft-note` returning `DraftNoteResponse(String front, String back)`; 404 via `NotFoundException` when no draft exists

- [ ] **Step 1: Create `DraftNoteResponse.java`**

```java
package com.felixkroemer.smort.application.deck.dto;

public record DraftNoteResponse(String front, String back) {}
```

- [ ] **Step 2: Create `DraftNoteRestMapper.java`**

```java
package com.felixkroemer.smort.application.deck.mapping;

import com.felixkroemer.smort.application.deck.dto.DraftNoteResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DraftNoteRestMapper {

  DraftNoteResponse toDraftNoteResponse(DraftNoteEntity draftNoteEntity);
}
```

- [ ] **Step 3: Add the endpoint in `DeckController.java`**

Add the field `private final DraftNoteRestMapper draftNoteRestMapper;` (the controller is `@RequiredArgsConstructor`) and the import `com.felixkroemer.smort.application.deck.mapping.DraftNoteRestMapper`. Add the endpoint next to the other deck chat/format endpoints:

```java
  @GetMapping("/{deckId}/draft-note")
  public DraftNoteResponse getDraftNote(@PathVariable("deckId") UUID deckId) {
    return draftNoteRestMapper.toDraftNoteResponse(deckService.getDraftNote(deckId));
  }
```

`DraftNoteResponse` and `DeckService.getDraftNote` are in the same package / already imported via `com.felixkroemer.smort.application.deck.dto.DraftNoteResponse` (add this import).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/application/deck/dto/DraftNoteResponse.java \
       src/main/java/com/felixkroemer/smort/application/deck/mapping/DraftNoteRestMapper.java \
       src/main/java/com/felixkroemer/smort/application/deck/DeckController.java
git commit -m "feat: add draft note read endpoint"
```

---

## Notes for the Implementer

- Compilation was intentionally skipped per AGENTS.md; the human owns build verification.
- The fixed sort key `META#DRAFTNOTE#` guarantees one draft per deck — a new draft overwrites the previous one.
- Do not create actual notes from the draft, add clearing, or user-edits to the draft — these are out of scope.
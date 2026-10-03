# Chat Package Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split `com.felixkroemer.smort.domain.chat` into three namespaces (root shared model, `domain.chat.orchestration`, `domain.chat.llm`) and rename the `*ChatMessage` domain classes to `*ChatResponse`.

**Architecture:** Pure package reorganization + renames — no behavior or logic changes. Dependency direction is `domain.chat.llm` -> `domain.chat` (root) and `domain.chat.orchestration` -> `domain.chat.llm` + `domain.chat` (root), with no cycles.

**Tech Stack:** Java 21, Spring Boot, MapStruct, DynamoDB Enhanced Client.

## Global Constraints

- Pure reorganization: do NOT change any method signature, field, string literal, or behavior. Only move files, adjust `package`/`import` declarations, and rename classes.
- Rename mapping (domain model only): `ChatMessage` -> `ChatResponse`, `TextChatMessage` -> `TextChatResponse`, `StoreNoteToolChatMessage` -> `StoreNoteToolChatResponse`, `DraftNoteToolChatMessage` -> `DraftNoteToolChatResponse`.
- `ChatMessageMeta` and the infra `ChatMessageEntity` are NOT renamed.
- Root `domain.chat` keeps (after rename): `ChatResponse`, `TextChatResponse`, `StoreNoteToolChatResponse`, `DraftNoteToolChatResponse`, `ChatMessageMeta`, `ChatContext`, `NoteChatContext`, `DeckChatContext`, `ChatUtil`, `ToolCallHandler`.
- `domain.chat.orchestration` gets: `ChatOrchestrationService`, `UserActionContextService`.
- `domain.chat.llm` gets: `NoteChatService`, `DeckChatService`, `NoteChatTools`, `DeckChatTools`, `NoteChatToolType`, `DeckChatToolType`.
- Compilation is owned by the human. Implementing subagents must NOT run `./mvnw compile`/`./mvnw test` or any build. Note in reports that compilation was skipped per AGENTS.md.
- Do NOT combine the near-duplicate methods (`handleStoreNoteToolResponse`/`handleDraftNoteToolResponse`, the repeated single-output extraction, the `acknowledge*ToolCall` pair). That is explicitly out of scope.

---

### Task 1: Rename the `*ChatMessage` domain model classes to `*ChatResponse`

**Files:**
- Rename: `src/main/java/com/felixkroemer/smort/domain/chat/ChatMessage.java` -> `ChatResponse.java`
- Rename: `src/main/java/com/felixkroemer/smort/domain/chat/TextChatMessage.java` -> `TextChatResponse.java`
- Rename: `src/main/java/com/felixkroemer/smort/domain/chat/StoreNoteToolChatMessage.java` -> `StoreNoteToolChatResponse.java`
- Rename: `src/main/java/com/felixkroemer/smort/domain/chat/DraftNoteToolChatMessage.java` -> `DraftNoteToolChatResponse.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/chat/ToolCallHandler.java`

**Interfaces:**
- Consumes: nothing from this plan (existing code only).
- Produces: `com.felixkroemer.smort.domain.chat.ChatResponse` (sealed interface permitting `TextChatResponse`, `StoreNoteToolChatResponse`, `DraftNoteToolChatResponse`), and `ToolCallHandler.execute(TransactWriteItemsEnhancedRequest.Builder tx, ChatResponse toolCall)`. All later tasks use these names.

- [ ] **Step 1: Rename the four files**

```bash
cd src/main/java/com/felixkroemer/smort/domain/chat
mv ChatMessage.java ChatResponse.java
mv TextChatMessage.java TextChatResponse.java
mv StoreNoteToolChatMessage.java StoreNoteToolChatResponse.java
mv DraftNoteToolChatMessage.java DraftNoteToolChatResponse.java
```

- [ ] **Step 2: Update `ChatResponse.java` (the sealed interface)**

Its content becomes:
```java
package com.felixkroemer.smort.domain.chat;

public sealed interface ChatResponse
    permits TextChatResponse, StoreNoteToolChatResponse, DraftNoteToolChatResponse {}
```

- [ ] **Step 3: Rename the three record classes in their files**

`TextChatResponse.java`:
```java
package com.felixkroemer.smort.domain.chat;

public record TextChatResponse(String response, ChatMessageMeta meta)
    implements ChatResponse {}
```

`StoreNoteToolChatResponse.java`:
```java
package com.felixkroemer.smort.domain.chat;

public record StoreNoteToolChatResponse(
    String callId, String front, String back, ChatMessageMeta meta)
    implements ChatResponse {}
```

`DraftNoteToolChatResponse.java`:
```java
package com.felixkroemer.smort.domain.chat;

public record DraftNoteToolChatResponse(
    String callId, String front, String back, ChatMessageMeta meta)
    implements ChatResponse {}
```

- [ ] **Step 4: Update `ToolCallHandler.java`**

Change the `execute` parameter type from `ChatMessage` to `ChatResponse`:
```java
package com.felixkroemer.smort.domain.chat;

import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@FunctionalInterface
public interface ToolCallHandler {

  void execute(TransactWriteItemsEnhancedRequest.Builder tx, ChatResponse toolCall);
}
```

- [ ] **Step 5: Update same-package references in `ChatOrchestrationService.java`, `NoteChatService.java`, `DeckChatService.java`**

In these three files (still in `domain.chat` at this point, so no imports needed), replace every occurrence:
- `ChatMessage` -> `ChatResponse`
- `TextChatMessage` -> `TextChatResponse`
- `StoreNoteToolChatMessage` -> `StoreNoteToolChatResponse`
- `DraftNoteToolChatMessage` -> `DraftNoteToolChatResponse`

This includes: the `switch (chatMessage)` variable/pattern types, `instanceof TextChatResponse(...)`, constructor calls like `new StoreNoteToolChatResponse(...)`, method return types, and `Map<Class<? extends ChatResponse>, ToolCallHandler>` generic usages.

- [ ] **Step 6: Update external references to the renamed classes**

These files (outside `domain/chat`) reference the renamed classes and must be updated (they use imports for `ChatMessage`/`StoreNoteToolChatMessage`/`DraftNoteToolChatMessage`; adjust the class names and imports):
- `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java` — `ChatMessage`, `StoreNoteToolChatMessage` -> `ChatResponse`, `StoreNoteToolChatResponse`
- `src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java` — `ChatMessage`, `StoreNoteToolChatMessage` -> `ChatResponse`, `StoreNoteToolChatResponse`
- `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java` — `ChatMessage`, `DraftNoteToolChatMessage` -> `ChatResponse`, `DraftNoteToolChatResponse`
- `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisBulkFormatService.java` — `ChatMessage`, `StoreNoteToolChatMessage` -> `ChatResponse`, `StoreNoteToolChatResponse`
- `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java` — `ChatMessage`, `StoreNoteToolChatMessage` -> `ChatResponse`, `StoreNoteToolChatResponse`

For each, rename the type usages and update the corresponding `import com.felixkroemer.smort.domain.chat.<X>` to the new class name.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(chat): rename ChatMessage model classes to ChatResponse"
```

---

### Task 2: Move orchestration classes into `domain.chat.orchestration`

**Files:**
- Move: `src/main/java/com/felixkroemer/smort/domain/chat/ChatOrchestrationService.java` -> `src/main/java/com/felixkroemer/smort/domain/chat/orchestration/ChatOrchestrationService.java`
- Move: `src/main/java/com/felixkroemer/smort/domain/chat/UserActionContextService.java` -> `src/main/java/com/felixkroemer/smort/domain/chat/orchestration/UserActionContextService.java`

**Interfaces:**
- Consumes: `com.felixkroemer.smort.domain.chat.ChatResponse` + tool responses (from Task 1).
- Produces: `com.felixkroemer.smort.domain.chat.orchestration.ChatOrchestrationService` and `...UserActionContextService`. Later tasks update external consumers to import these new FQNs.

- [ ] **Step 1: Create the `orchestration` package directory**

```bash
mkdir -p src/main/java/com/felixkroemer/smort/domain/chat/orchestration
```

- [ ] **Step 2: Move `ChatOrchestrationService.java` and update its package + imports**

Move the file to `orchestration/`. Change line 1 to `package com.felixkroemer.smort.domain.chat.orchestration;`.

Add these imports (the class now needs explicit imports for types it used from the same package):
```java
import com.felixkroemer.smort.domain.chat.ChatMessageMeta;
import com.felixkroemer.smort.domain.chat.ChatResponse;
import com.felixkroemer.smort.domain.chat.DraftNoteToolChatResponse;
import com.felixkroemer.smort.domain.chat.StoreNoteToolChatResponse;
import com.felixkroemer.smort.domain.chat.TextChatResponse;
import com.felixkroemer.smort.domain.chat.ToolCallHandler;
import com.felixkroemer.smort.domain.chat.llm.DeckChatService;
import com.felixkroemer.smort.domain.chat.llm.NoteChatService;
```
Keep the existing imports for `SmortException`, the infra chat classes (`AbstractChatMessageEntity`, `ChatMessageEntity`, `ChatRepository`), `Instant`, `HashMap`, `List`, `Map`, `Optional`, `UUID`, `RequiredArgsConstructor`, `NonNull`, `Service`, `DynamoDbEnhancedClient`, `TransactWriteItemsEnhancedRequest`.

- [ ] **Step 3: Move `UserActionContextService.java` and update its package**

Move the file to `orchestration/`. Change line 1 to `package com.felixkroemer.smort.domain.chat.orchestration;`.

This class references no same-package chat types, so no new imports are needed. Keep existing imports (`JsonProcessingException`, `ObjectMapper`, `SmortException`, infra chat classes, `ArrayList`, `Map`, `RequiredArgsConstructor`, `Service`).

- [ ] **Step 4: Verify no leftover files in the old package**

Run: `ls src/main/java/com/felixkroemer/smort/domain/chat/`
Expected: `ChatOrchestrationService.java` and `UserActionContextService.java` are gone; all remaining files are the root model classes plus the `llm`/`orchestration` subdirectories.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(chat): move orchestration services into domain.chat.orchestration"
```

---

### Task 3: Move LLM chat classes into `domain.chat.llm`

**Files:**
- Move: `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java` -> `.../domain/chat/llm/NoteChatService.java`
- Move: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java` -> `.../domain/chat/llm/DeckChatService.java`
- Move: `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatTools.java` -> `.../domain/chat/llm/NoteChatTools.java`
- Move: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatTools.java` -> `.../domain/chat/llm/DeckChatTools.java`
- Move: `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatToolType.java` -> `.../domain/chat/llm/NoteChatToolType.java`
- Move: `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatToolType.java` -> `.../domain/chat/llm/DeckChatToolType.java`

**Interfaces:**
- Consumes: `com.felixkroemer.smort.domain.chat.ChatResponse` + tool responses (from Task 1).
- Produces: `com.felixkroemer.smort.domain.chat.llm.NoteChatService`, `...DeckChatService`, `...NoteChatTools`, `...DeckChatTools`, `...NoteChatToolType`, `...DeckChatToolType`. Later tasks update external consumers.

- [ ] **Step 1: Create the `llm` package directory**

```bash
mkdir -p src/main/java/com/felixkroemer/smort/domain/chat/llm
```

- [ ] **Step 2: Move the six LLM files and update packages**

Move each file into `llm/`. For each, change the package declaration from `package com.felixkroemer.smort.domain.chat;` to `package com.felixkroemer.smort.domain.chat.llm;`.

For `NoteChatService.java`, add these imports (same-package types it used):
```java
import com.felixkroemer.smort.domain.chat.ChatMessageMeta;
import com.felixkroemer.smort.domain.chat.ChatResponse;
import com.felixkroemer.smort.domain.chat.ChatUtil;
import com.felixkroemer.smort.domain.chat.NoteChatContext;
import com.felixkroemer.smort.domain.chat.StoreNoteToolChatResponse;
import com.felixkroemer.smort.domain.chat.TextChatResponse;
```
Keep existing imports (`ObjectMapper`, `SmortException`, `NoteSchema`, `OpenAIClient`, `com.openai.models.responses.*`, `Instant`, `List`, `Map`, `Optional`, `RequiredArgsConstructor`, `Value`, `Service`).

For `DeckChatService.java`, add these imports:
```java
import com.felixkroemer.smort.domain.chat.ChatMessageMeta;
import com.felixkroemer.smort.domain.chat.ChatResponse;
import com.felixkroemer.smort.domain.chat.ChatUtil;
import com.felixkroemer.smort.domain.chat.DeckChatContext;
import com.felixkroemer.smort.domain.chat.DraftNoteToolChatResponse;
import com.felixkroemer.smort.domain.chat.TextChatResponse;
```
Keep existing imports (`SmortException`, `OpenAIClient`, `com.openai.models.responses.*`, `Instant`, `List`, `Optional`, `RequiredArgsConstructor`, `Value`, `Service`).

`NoteChatTools.java`, `DeckChatTools.java`, `NoteChatToolType.java`, `DeckChatToolType.java` reference no same-package chat types — only change their `package` declarations.

- [ ] **Step 3: Verify the root package now contains only model classes**

Run: `ls src/main/java/com/felixkroemer/smort/domain/chat/`
Expected: `ChatContext.java`, `ChatMessageMeta.java`, `ChatResponse.java`, `ChatUtil.java`, `DeckChatContext.java`, `DraftNoteToolChatResponse.java`, `NoteChatContext.java`, `StoreNoteToolChatResponse.java`, `TextChatResponse.java`, `ToolCallHandler.java`, plus `llm/` and `orchestration/` subdirectories.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(chat): move llm chat services into domain.chat.llm"
```

---

### Task 4: Update external consumers of `ChatOrchestrationService`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckBulkFormatService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnalysisBulkFormatService.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/deck/DeckController.java`
- Modify: `src/main/java/com/felixkroemer/smort/application/anki/AnalysisController.java`

**Interfaces:**
- Consumes: `com.felixkroemer.smort.domain.chat.orchestration.ChatOrchestrationService`.
- Produces: nothing new.

- [ ] **Step 1: Update the import in each file**

In each of the five files, replace:
```java
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
```
with:
```java
import com.felixkroemer.smort.domain.chat.orchestration.ChatOrchestrationService;
```

No other changes in these files.

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor(chat): update ChatOrchestrationService imports in consumers"
```

---

### Task 5: Update wildcard-importing consumers and the `DeckChatToolType` consumer

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/NoteService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/anki/AnkiNoteService.java`
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`

**Interfaces:**
- Consumes: `com.felixkroemer.smort.domain.chat.orchestration.ChatOrchestrationService`, root model classes, `com.felixkroemer.smort.domain.chat.llm.DeckChatToolType`.
- Produces: nothing new.

- [ ] **Step 1: Replace the wildcard import in `NoteService.java`**

Both `NoteService.java` and `AnkiNoteService.java` currently use:
```java
import com.felixkroemer.smort.domain.chat.*;
```
Replace it with explicit imports. For `NoteService.java` use:
```java
import com.felixkroemer.smort.domain.chat.ChatResponse;
import com.felixkroemer.smort.domain.chat.NoteChatContext;
import com.felixkroemer.smort.domain.chat.StoreNoteToolChatResponse;
import com.felixkroemer.smort.domain.chat.ToolCallHandler;
import com.felixkroemer.smort.domain.chat.orchestration.ChatOrchestrationService;
```

- [ ] **Step 2: Replace the wildcard import in `AnkiNoteService.java`**

For `AnkiNoteService.java` use the same five imports as `NoteService.java`:
```java
import com.felixkroemer.smort.domain.chat.ChatResponse;
import com.felixkroemer.smort.domain.chat.NoteChatContext;
import com.felixkroemer.smort.domain.chat.StoreNoteToolChatResponse;
import com.felixkroemer.smort.domain.chat.ToolCallHandler;
import com.felixkroemer.smort.domain.chat.orchestration.ChatOrchestrationService;
```

- [ ] **Step 3: Update `DeckChatToolType` import in `DeckService.java`**

`DeckService.java` currently imports `com.felixkroemer.smort.domain.chat.DeckChatToolType` (it also imports `ChatOrchestrationService`, handled in Task 4). Replace:
```java
import com.felixkroemer.smort.domain.chat.DeckChatToolType;
```
with:
```java
import com.felixkroemer.smort.domain.chat.llm.DeckChatToolType;
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(chat): update explicit and wildcard chat imports in consumers"
```

---

### Task 6: Verify the reorganization compiles (human-owned)

**Files:**
- None (verification only).

**Interfaces:**
- Consumes: all of Task 1-5 output.

- [ ] **Step 1: Grep for any remaining stale imports of moved classes from the old package**

Run from the repo root:
```bash
grep -rn "import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;" src/main/java
grep -rn "import com.felixkroemer.smort.domain.chat.UserActionContextService;" src/main/java
grep -rn "import com.felixkroemer.smort.domain.chat.NoteChatService;" src/main/java
grep -rn "import com.felixkroemer.smort.domain.chat.DeckChatService;" src/main/java
grep -rn "import com.felixkroemer.smort.domain.chat.NoteChatTools;" src/main/java
grep -rn "import com.felixkroemer.smort.domain.chat.DeckChatTools;" src/main/java
grep -rn "import com.felixkroemer.smort.domain.chat.NoteChatToolType;" src/main/java
grep -rn "import com.felixkroemer.smort.domain.chat.DeckChatToolType;" src/main/java
```
Expected: no output (no stale imports remain).

- [ ] **Step 2: Grep for any remaining old class names**

Run from the repo root:
```bash
grep -rn "\bChatMessage\b" src/main/java --include=*.java | grep -v "ChatMessageEntity" | grep -v "ChatMessageMeta"
```
Expected: no output (all `ChatMessage` references renamed to `ChatResponse`, except the intentionally-kept `ChatMessageEntity`/`ChatMessageMeta`).

- [ ] **Step 3: Confirm only model classes remain in the root chat package**

Run: `ls src/main/java/com/felixkroemer/smort/domain/chat/`
Expected: `ChatContext.java`, `ChatMessageMeta.java`, `ChatResponse.java`, `ChatUtil.java`, `DeckChatContext.java`, `DraftNoteToolChatResponse.java`, `NoteChatContext.java`, `StoreNoteToolChatResponse.java`, `TextChatResponse.java`, `ToolCallHandler.java`, plus `llm/` and `orchestration/` subdirectories.

- [ ] **Step 4: Hand off to the human for compilation**

Compilation is owned by the human per AGENTS.md. Report to the human that compilation was skipped and request they run `./mvnw compile` to confirm the reorganization builds. Do not run the build yourself.

- [ ] **Step 5: Commit any final adjustments**

If the human reports compile errors (e.g., a missing import I didn't enumerate), fix them, then:
```bash
git add -A
git commit -m "refactor(chat): fix remaining imports after package split"
```

---

## Self-Review

- **Spec coverage:** All three namespaces from the spec (root model, `orchestration`, `llm`) are covered (Tasks 2-3), the rename is covered (Task 1), and all external consumers are updated (Tasks 4-5), with verification (Task 6). Dependency direction and "no cycles" hold because moved `llm`/`orchestration` classes import only root model classes (same-package tools/types stay together).
- **Placeholder scan:** No TBD/TODO. Every rename mapping, import list, and verification command is concrete per file.
- **Type consistency:** The rename in Task 1 produces `ChatResponse` + `*ChatResponse` names used consistently in Tasks 2-3 imports and Task 5 consumer imports. Import FQNs used in Tasks 4-5 match the package declarations set in Tasks 2-3 (`domain.chat.orchestration.ChatOrchestrationService`, `domain.chat.llm.*`). `ChatMessageMeta` and infra `ChatMessageEntity` are preserved unchanged throughout.
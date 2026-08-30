# Chat Clearing and Unified Async Deletion Design

## Goal

1. Allow clearing the chat for a deck, a deck note, and an anki note via new DELETE endpoints.
2. Delete chats (and the deck bulk-format job) when a deck is deleted.
3. Unify deck and analysis deletion: both are marked for deletion and cleaned up asynchronously by a single cron.
4. Add an asynchronous cron that deletes analyses marked for deletion (analyses are currently deleted synchronously).

## Background: key scheme and current flows

- All DynamoDB entities live in one `common-table`, keyed by `pk`/`sk` only.
- A chat message: `pk` = container (`DECK#<deckId>` or `ANALYSIS#<analysisId>`); `sk` = `CHAT#C#<entityId>#<createdAt>#<responseId>` (assistant) or `CHAT#U#<entityId>#...` (user-initiated tool call). The `entityId` is the subject: deckId (UUID) for deck chat, noteId (UUID) for deck note chat, noteId (Long) for anki note chat.
- No chat deletion exists anywhere. `ChatRepository` only has `findLatestChatMessage`, `findAll`, `save`, `saveInTx`.
- `DeckCron.deleteDecksMarkedForDeletion()` scans decks with `status == MARKED_FOR_DELETION` and deletes notes + draft note + meta. It leaks chats and the deck bulk-format job (`BulkFormatRepository.deleteDeckJob` exists but is never called).
- `AnalysisService.deleteAnalysis()` sets `MARKED_FOR_DELETION` and then deletes synchronously: db file, derived notes, bulk-format job, meta. It leaks chats. There is no cron for analyses.
- `AnalysisMetaRepository.findAllAnalysisMetas()` (used by `AnalysisService.getAnalyses()`) filters `sk = META# AND begins_with(pk, ANALYSIS#)` but does NOT filter by status, so marked-for-deletion analyses appear in listings.
- `@EnableScheduling` is active; crons follow the pattern: `@Scheduled(cron = "${app.scheduling.<key>}")`, property in `application.properties`, POST trigger in `CronController`.

## Design

### Part A — Clear chats

**`ChatRepository`** gains two batch-delete methods, mirroring the `DeckRepository.deleteDeckNotes` pattern (query projecting `pk`/`sk`, batch-delete chunked by 25 via `DynamoDbEnhancedClient`). `ChatRepository` gains a `DynamoDbEnhancedClient` dependency.

- `deleteAll(String pk)` — queries `pk` with `sk begins_with "CHAT#"` and batch-deletes every chat under the container (used by deck/analysis cleanup).
- `deleteChat(String pk, Object entityId)` — queries both `ChatKeys.llmChatMessagesPrefix(entityId)` and `ChatKeys.userChatMessagesPrefix(entityId)` (mirrors `findAll`) and batch-deletes them (used by the clear endpoints).

**Service clear methods** (each delegates to `chatRepository.deleteChat`):

- `DeckService.clearChat(UUID deckId)` → `deleteChat(DeckKeys.deckPk(deckId), deckId)`
- `NoteService.clearChat(UUID deckId, UUID noteId)` → `deleteChat(DeckKeys.deckPk(deckId), noteId)`
- `AnkiNoteService.clearChat(UUID analysisId, Long noteId)` → `deleteChat(AnalysisKeys.analysisPk(analysisId), noteId)`

**Endpoints** (all `DELETE`, `@ResponseStatus(HttpStatus.NO_CONTENT)`, matching the existing draft-note clear style):

- `DELETE /decks/{deckId}/chat` → `deckService.clearChat(deckId)`
- `DELETE /decks/{deckId}/notes/{noteId}/chat` → `noteService.clearChat(deckId, noteId)`
- `DELETE /analysis/{analysisId}/notes/{noteId}/chat` → `ankiNoteService.clearChat(analysisId, noteId)`

### Part B — Unified async deletion via `CleanupCron`

**`CleanupCron`** (renamed from `DeckCron`, new file at `domain/cron/CleanupCron.java`; old `DeckCron.java` deleted). Both scheduled cleanups live directly in the cron, each iterating marked-for-deletion entities with a per-entity try/catch. Dependencies: `DeckRepository`, `DraftNoteRepository`, `BulkFormatRepository`, `ChatRepository`, `AnalysisMetaRepository`, `DerivedNoteRepository`.

```java
@Scheduled(cron = "${app.scheduling.delete-marked-decks-cron}")
public void deleteDecksMarkedForDeletion() {
  for (var deck : deckRepository.scanForDecksMarkedForDeletion()) {
    try {
      deckRepository.deleteDeckNotes(deck.getDeckId());
      draftNoteRepository.delete(deck.getDeckId());
      bulkFormatRepository.deleteDeckJob(deck.getDeckId());
      chatRepository.deleteAll(DeckKeys.deckPk(deck.getDeckId()));
      deckRepository.deleteDeckMeta(deck.getDeckId());
    } catch (Exception e) {
      log.error("Could not fully delete deck marked for deletion. deckId={}", deck.getDeckId());
    }
  }
}

@Scheduled(cron = "${app.scheduling.delete-marked-analyses-cron}")
public void deleteAnalysesMarkedForDeletion() {
  for (var analysis : analysisMetaRepository.scanForAnalysesMarkedForDeletion()) {
    try {
      if (analysis.getDbPath() != null) {
        Files.deleteIfExists(Path.of(analysis.getDbPath()));
      }
      derivedNoteRepository.deleteAnalysisDerivedNotes(analysis.getAnalysisId());
      bulkFormatRepository.delete(analysis.getAnalysisId());
      chatRepository.deleteAll(AnalysisKeys.analysisPk(analysis.getAnalysisId()));
      analysisMetaRepository.delete(analysis.getAnalysisId());
    } catch (Exception e) {
      log.warn("Could not fully delete analysis. analysisId={}", analysis.getAnalysisId(), e);
    }
  }
}
```

**`DeckService`** — `deleteDeck(deckId)` unchanged (mark only).

**`AnalysisService`** — `deleteAnalysis(analysisId)` becomes mark-only (drops the synchronous cleanup; the `try`/`catch`/`Files`/`Path` deletion moves to the cron):

```java
public void deleteAnalysis(UUID analysisId) {
  var analysis = getMeta(analysisId);
  analysis.setStatus(AnalysisStatus.MARKED_FOR_DELETION);
  analysisMetaRepository.save(analysis);
}
```

**`AnalysisMetaRepository`**:

- `findAllAnalysisMetas()` — add active-status filtering: the existing `#sk = :sk AND begins_with(#pk, :pkPrefix)` expression gains `AND #status <> :status` with `:status = AnalysisStatus.MARKED_FOR_DELETION.name()`. No parameter. So `getAnalyses()` no longer lists marked-for-deletion analyses.
- New `scanForAnalysesMarkedForDeletion()` — same scan shape but `#status = :status` (`MARKED_FOR_DELETION`), mirroring `DeckRepository.scanForDecksMarkedForDeletion`.

**`CronController`** — `deckCron` replaced by `cleanupCron`; keep `POST /deleteDecksMarkedForDeletion`, add `POST /deleteAnalysesMarkedForDeletion`.

**`application.properties`** — add `app.scheduling.delete-marked-analyses-cron:0 * * * * *` (mirrors the deck cron).

## Files

New:
- `domain/cron/CleanupCron.java`

Modified:
- `infrastructure/dynamodb/chat/ChatRepository.java` (add `deleteAll`, `deleteChat`, `DynamoDbEnhancedClient`)
- `domain/deck/DeckService.java` (add `clearChat`)
- `domain/deck/NoteService.java` (add `clearChat`)
- `domain/anki/AnkiNoteService.java` (add `clearChat`)
- `application/deck/DeckController.java` (2 new DELETE endpoints)
- `application/anki/AnalysisController.java` (1 new DELETE endpoint)
- `domain/anki/AnalysisService.java` (`deleteAnalysis` mark-only)
- `infrastructure/dynamodb/anki/AnalysisMetaRepository.java` (status filter on `findAllAnalysisMetas`; new `scanForAnalysesMarkedForDeletion`)
- `application/cron/CronController.java` (`cleanupCron`; new trigger)
- `src/main/resources/application.properties` (new cron property)

Deleted:
- `domain/cron/DeckCron.java`

## Error handling

- Clear endpoints return `204 No Content`; empty chats are a no-op (query returns no items).
- Missing deck/analysis meta on `deleteAnalysis` → `NotFoundException` via `getMeta`, as today.
- Cron per-entity try/catch prevents one failure from aborting the batch; failures are logged and retried on the next tick.

## Testing

None. Tests are only written when explicitly requested per project convention.

## Out of scope

- `EntityManagerFactoryCache` eviction on analysis delete (cache expires after 30 min; deleting the DB file is safe on Linux).
- Refactoring the `BulkFormatCron` or its trigger.
- Changing `deleteNote`/`getNotes`/`getDraftNote` or other deck methods.
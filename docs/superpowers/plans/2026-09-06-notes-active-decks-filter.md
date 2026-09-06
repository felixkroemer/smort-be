# Notes Active-Decks Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `GET /notes` return only notes whose deck is `ACTIVE`, so notes from `IMPORTING` and `MARKED_FOR_DELETION` decks never appear in the all-notes list.

**Architecture:** The filter lives in `DeckService.getAllNotes()`. It collects the deck ids of the user's active decks from the existing `getDecks()` service method (which returns only `ACTIVE` decks) and keeps only the notes whose `deckId` is in that set. Notes with a `null` deckId are excluded implicitly.

**Tech Stack:** Java 17, Spring, Maven. No framework additions.

## Global Constraints

- Do not write tests (per AGENTS.md — tests only when explicitly requested). No test commands run.
- Do not run or debug the build (`./mvnw compile`, `./mvnw test`); compilation is owned by the human and skipped. Note this in the completion report.
- No repository changes, no method renames, no new query methods — the spec forbids them.
- The current user is the hardcoded dummy `"default"`; `getDecks()` and `findNotesByUserId` both target it.
- Commit on the feature branch `feat/all-notes-with-deck`.

---

### Task 1: Filter all-notes to active decks in `getAllNotes()`

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java:73-75`

**Interfaces:**
- Consumes: `DeckService.getDecks()` returning `List<Deck>` (already used by `DeckController`, no signature change); `DeckRepository.findNotesByUserId(String userId)` returning `List<NoteEntity>`; existing imports `Deck` (DeckService.java:7), `Collectors` (DeckService.java:43), `NoteEntity` (DeckService.java:32).
- Produces: `DeckService.getAllNotes()` now returns only notes whose `deckId` matches an active deck — consumed by `NoteController.getAllNotes()`.

- [ ] **Step 1: Replace the body of `getAllNotes()`**

In `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`, replace lines 73-75 with:

```java
  public List<NoteEntity> getAllNotes() {
    var deckIds = getDecks().stream().map(Deck::getDeckId).collect(Collectors.toSet());
    return deckRepository.findNotesByUserId("default").stream()
        .filter(note -> deckIds.contains(note.getDeckId()))
        .toList();
  }
```

No other code changes — `Deck`, `Collectors`, `NoteEntity` are already imported.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java
git commit -m "feat: filter all-notes endpoint to active decks only"
```
# Chat Format Instructions — Design

Date: 2026-08-30
Status: Approved
Scope: Thread the entity's `formatInstructions` setting through the chat chains
(deck chat, deck note chat, analysis note chat), mirroring the existing
`formatNote` flow.

## Problem

The three chat flows — `DeckService.chat` (deck chat), `NoteService.chat`
(note chat in a deck), and `AnkiNoteService.chat` (note chat in an analysis) —
ignore the entity's `formatInstructions` setting. The underlying chat services
hardcode the default instructions: `NoteChatService.chat` passes
`ChatUtil.formatInstructions()` (no-arg, default rules) and `DeckChatService.chat`
passes `ChatUtil.formattingRules()`. The `formatNote` flow already threads the
setting end-to-end via `ChatUtil.formatInstructions(Optional<String>)`; chat
should do the same.

## Decisions

- Thread `Optional<String> formatInstructions` as an explicit parameter through
  the whole chain, mirroring how `formatNote` does it.
- Substitute via `ChatUtil.formatInstructions(formatInstructions)` in both chat
  services (full instruction block, formatNote-style). This changes
  `DeckChatService`'s default prompt from rules-only to the full block — which
  is what `NoteChatService.chat` already does today. Confirmed by the user.
- Callers fetch the setting via the dedicated settings accessors
  (`analysisService.getAnalysisSettings(...)` / `deckService.getDeckSettings(...)`),
  which are lighter reads than loading the full `Analysis`/`Deck`.
- No new dependencies: `NoteService` already depends on `DeckService`,
  `AnkiNoteService` already depends on `AnalysisService`, and the chat services
  are unchanged in their dependencies.
- No tests, no build verification (per AGENTS.md).

## Chain

### Chat orchestration (`ChatOrchestrationService`)

Add `Optional<String> formatInstructions` as the last-before-`toolHandlers`
parameter (matching `formatNote`):

- `noteChat(String pk, NoteChatContext<?> ctx, String message,
  Optional<String> formatInstructions, Map<...> toolHandlers)` →
  `noteChatService.chat(ctx, message, formatInstructions, latestChatMessageResponseId)`.
- `deckChat(String pk, DeckChatContext ctx, String message,
  Optional<String> formatInstructions, Map<...> toolHandlers)` →
  `deckChatService.chat(ctx, message, formatInstructions, latestChatMessageResponseId)`.

### Chat services

- `NoteChatService.chat(NoteChatContext<?> ctx, String message,
  Optional<String> formatInstructions, Optional<String> previousResponseId)`:
  replace `ChatUtil.formatInstructions()` with
  `ChatUtil.formatInstructions(formatInstructions)` in the `CHAT_INSTRUCTIONS`
  substitution.
- `DeckChatService.chat(DeckChatContext ctx, String message,
  Optional<String> formatInstructions, Optional<String> previousResponseId)`:
  replace `ChatUtil.formattingRules()` with
  `ChatUtil.formatInstructions(formatInstructions)` in the `CHAT_INSTRUCTIONS`
  substitution.

### Callers

- `AnkiNoteService.chat(analysisId, noteId, message)`:
  `var formatInstructions = analysisService.getAnalysisSettings(analysisId).formatInstructions();`
  then pass to `chatOrchestrationService.noteChat(...)`.
- `NoteService.chat(deckId, noteId, message)`:
  `var formatInstructions = deckService.getDeckSettings(deckId).formatInstructions();`
  then pass to `chatOrchestrationService.noteChat(...)`.
- `DeckService.chat(deckId, message)`:
  `var formatInstructions = getDeckSettings(deckId).formatInstructions();`
  then pass to `chatOrchestrationService.deckChat(...)`.

## Branching

Implemented on branch `feat/chat-format-instructions`, branched from
`feat/deck-format-settings` (this work depends on the deck settings accessors
introduced there, which are not yet merged to main).
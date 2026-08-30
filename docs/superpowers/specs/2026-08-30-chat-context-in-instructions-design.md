# Chat Context in Instructions Design

Date: 2026-08-30

## Goal

Make the deck and note chat send *all* context via the model `instructions` and pass the
user's message through unchanged. In addition, the deck chat must embed the current draft
note (if any) into the context so the model can reference it across turns, with an explicit
fallback message when no draft exists.

## Background

The Responses API does not carry `instructions` over between requests that use
`previousResponseId` — only the `input` chain persists. Therefore any context the model must
reliably see on every turn has to be re-sent with each request. Both chat services already
re-send context (the notes list / formatting rules) inside `CHAT_INSTRUCTIONS` on every call.

Current problems:

- `DeckChatService.chat` prefixes the user message with the deck name
  (`"Deck: " + ctx.deckName() + "\n\n" + message`) instead of providing it as context.
- `NoteChatService.chat` prefixes the user message with the note's fields
  (`"Fields:\n..." + message`) instead of providing them as context.
- The current draft note (produced by the DraftNote tool) is not provided to the model at all;
  the model can only infer it from conversation history, which is unreliable.

## Scope

In scope:

- `DeckChatContext`, `DeckChatService`, `DeckService` (draft fetch + context construction).
- `NoteChatContext` (unchanged record; only its consumer changes), `NoteChatService`.
- `NoteChatService.acknowledgeStoreNoteToolCall` (required to keep compiling once the shared
  `CHAT_INSTRUCTIONS` placeholder count changes).

Out of scope:

- `NoteChatService.formatNote` (structured formatting path — there the input *is* the data).
- The DraftNote / StoreNote tool flows, persistence, REST endpoints, cron cleanup.
- `ChatOrchestrationService` (no signature changes).

## Design

### Deck chat

**`DeckChatContext`** gains a fourth component:

```java
public record DeckChatContext(
    UUID deckId, String deckName, List<String> notes, Optional<NoteSchema> draft)
    implements ChatContext {}
```

`NoteSchema` (domain.common) is reused for the draft's front/back; using a plain domain value
avoids a domain → infrastructure dependency (`DraftNoteEntity` lives in
`infrastructure.dynamodb.deck`).

**`DeckService.chat`** (DeckService.java:167-187): after fetching notes, fetch the draft and
build the context:

```java
var draft =
    draftNoteRepository
        .findDraftNote(deckId)
        .map(d -> new NoteSchema(d.getFront(), d.getBack()));

var ctx = new DeckChatContext(deckId, deck.getName(), notes, draft);
```

**`DeckChatService.chat`**: `CHAT_INSTRUCTIONS` gains two sections — the deck name and the
current draft:

```
Your task is to assist the user in learning about and improving their Anki deck.
You can discuss the deck's content, help identify gaps, and suggest improvements.

Deck: %s

When the user asks you to draft a new note for the deck, use the DraftNote tool.
The "front" should be the question or term, the "back" the answer or explanation.
Take the conversation into account: if the topic was discussed before, or the user asked
for clarifications or adjustments, reflect that in the note, but keep it concise,
not overly verbose.

For the formatting, consider these rules:
%s

The deck currently contains these notes:
%s

Current draft note:
%s
```

The draft section value:

- present → `Front: <front>\nBack: <back>`
- absent → `No draft note exists right now.`

`input` becomes just the user `message` (the deck-name prefix is removed):

```java
.input(message)
```

Placeholder order for `.formatted(...)`: deck name, formatting rules, notes, draft section.

### Note chat

**`NoteChatService.chat`**: `CHAT_INSTRUCTIONS` gains a fields section:

```
Your task is to assist the user in fact-checking, learning about, and improving the anki ankiNote provided in the form of its fields.

The ankiNote has these fields:
%s

When you are asked to edit one or multiple fields in any way, use the tool for updating notes.
Then acknowledge with a short summary.

For the formatting, consider these rules:
%s
```

The fields section value is the existing `Fields:\n` block from the input prefix
(`key: value` per line). `input` becomes just the user `message`.

Placeholder order for `.formatted(...)`: fields, formatting rules.

**`NoteChatService.acknowledgeStoreNoteToolCall`**: `CHAT_INSTRUCTIONS` is currently reused
here (`.instructions(CHAT_INSTRUCTIONS.formatted(ChatUtil.formatInstructions()))`). Once the
fields placeholder is added, that call no longer compiles (wrong argument count) and the ack
has no context to supply fields. Introduce a dedicated minimal ack constant mirroring
`DeckChatService.DRAFT_ACK_INSTRUCTIONS`:

```java
private static final String NOTE_ACK_INSTRUCTIONS =
    """
    Confirm to the user that the note was updated. Keep it to one short sentence.
    """;
```

This is a small prompt-wording change for the note ack; accepted during design review.

## Error handling

- If the draft is missing, the fallback message is used; no error is thrown (this is the
  normal "no draft yet" state, not an error).
- No new failure modes: `findDraftNote` returns `Optional`; a DynamoDB failure would already
  propagate from the repository as before.

## Testing

Per project convention (AGENTS.md), no automated tests unless explicitly requested. The change
is prompt/context construction; verification is by build + manual chat interaction.

## Files touched

- `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatContext.java`
- `src/main/java/com/felixkroemer/smort/domain/chat/DeckChatService.java`
- `src/main/java/com/felixkroemer/smort/domain/chat/NoteChatService.java`
- `src/main/java/com/felixkroemer/smort/domain/deck/DeckService.java`
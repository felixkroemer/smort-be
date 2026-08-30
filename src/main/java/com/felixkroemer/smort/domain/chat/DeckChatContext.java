package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.domain.common.NoteSchema;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record DeckChatContext(
    UUID deckId, String deckName, List<String> notes, Optional<NoteSchema> draft)
    implements ChatContext {}

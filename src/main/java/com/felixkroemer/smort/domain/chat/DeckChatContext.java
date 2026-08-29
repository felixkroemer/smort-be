package com.felixkroemer.smort.domain.chat;

import java.util.List;
import java.util.UUID;

public record DeckChatContext(UUID deckId, String deckName, List<String> notes)
    implements ChatContext {}

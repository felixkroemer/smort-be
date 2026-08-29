package com.felixkroemer.smort.domain.chat;

import java.util.UUID;

public record DeckChatContext(UUID deckId, String deckName) implements ChatContext {}

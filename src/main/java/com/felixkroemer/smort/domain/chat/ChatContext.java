package com.felixkroemer.smort.domain.chat;

public sealed interface ChatContext permits NoteChatContext, DeckChatContext {}

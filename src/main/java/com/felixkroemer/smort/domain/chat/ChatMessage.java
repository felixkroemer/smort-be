package com.felixkroemer.smort.domain.chat;

public sealed interface ChatMessage permits TextChatMessage, StoreNoteToolChatMessage {}

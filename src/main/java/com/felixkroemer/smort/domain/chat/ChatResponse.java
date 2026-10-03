package com.felixkroemer.smort.domain.chat;

public sealed interface ChatResponse
    permits TextChatResponse, StoreNoteToolChatResponse, DraftNoteToolChatResponse {}
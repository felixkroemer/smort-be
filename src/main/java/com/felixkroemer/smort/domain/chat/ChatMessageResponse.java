package com.felixkroemer.smort.domain.chat;

public sealed interface ChatMessageResponse
    permits ChatMessageTextResponse, StoreNoteToolResponse {}

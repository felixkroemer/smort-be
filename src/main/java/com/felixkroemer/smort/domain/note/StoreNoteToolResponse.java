package com.felixkroemer.smort.domain.note;

import java.util.List;

public record StoreNoteToolResponse(
    String toolName, String callId, List<String> fields, ChatMessageResponseMeta meta)
    implements ChatMessageResponse {}

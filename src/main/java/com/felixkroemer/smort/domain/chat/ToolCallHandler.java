package com.felixkroemer.smort.domain.chat;

import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@FunctionalInterface
public interface ToolCallHandler {

  void execute(TransactWriteItemsEnhancedRequest.Builder tx, ChatMessage toolCall);
}

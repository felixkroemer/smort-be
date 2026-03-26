package com.felixkroemer.smort.infrastructure.dynamodb.chat;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class ChatMessageEntity {

  private UUID chatId;
  private UUID messageId;
  private String role;
  private String contentJson;
  private String responseId;
  private String previousResponseId;
  private Instant createdAt;

  public ChatMessageEntity(
      UUID chatId,
      String role,
      String contentJson,
      String responseId,
      String previousResponseId) {
    this.chatId = chatId;
    this.role = role;
    this.contentJson = contentJson;
    this.responseId = responseId;
    this.previousResponseId = previousResponseId;
    this.createdAt = Instant.now();
    this.messageId = UUID.randomUUID();
  }

  @DynamoDbPartitionKey
  public String getPk() {
    return "CHAT#" + chatId;
  }

  @DynamoDbSortKey
  public String getSk() {
    return "MESSAGE#" + createdAt.toString() + "#" + messageId;
  }
}

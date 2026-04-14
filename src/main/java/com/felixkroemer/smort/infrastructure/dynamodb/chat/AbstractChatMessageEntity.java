package com.felixkroemer.smort.infrastructure.dynamodb.chat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.felixkroemer.smort.infrastructure.dynamodb.OptionalStringConverter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractChatMessageEntity {

  private String content;
  private String responseId;
  private Optional<String> previousResponseId;
  private Instant createdAt;

  @DynamoDbConvertedBy(OptionalStringConverter.class)
  public Optional<String> getPreviousResponseId() {
    return previousResponseId;
  }
}

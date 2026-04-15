package com.felixkroemer.smort.infrastructure.dynamodb.chat;

import com.felixkroemer.smort.infrastructure.dynamodb.OptionalStringConverter;
import java.time.Instant;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractChatMessageEntity {

  private String response;
  private String message;
  private String responseId;
  private Optional<String> previousResponseId;
  private Instant createdAt;

  @DynamoDbConvertedBy(OptionalStringConverter.class)
  public Optional<String> getPreviousResponseId() {
    return previousResponseId;
  }
}

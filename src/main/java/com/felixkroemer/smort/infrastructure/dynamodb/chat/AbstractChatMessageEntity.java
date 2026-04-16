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

  private AbstractChatMessageEntityType type; // "TEXT" or "TOOL_CALL"

  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  private Optional<String> response; // only populated for TEXT

  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  private Optional<String> callId; // only populated for TOOL_CALL

  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  private Optional<String> toolName; // only populated for TOOL_CALL

  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  private Optional<String> message;

  private String responseId;

  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  private Optional<String> previousResponseId;

  private Instant createdAt;
}

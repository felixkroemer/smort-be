package com.felixkroemer.smort.infrastructure.dynamodb.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.OptionalInstantConverter;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class NoteEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private UUID id;
  private String front;
  private String back;

  @Getter(onMethod_ = @DynamoDbSecondaryPartitionKey(indexNames = "UserNoteIndex"))
  private String userNoteIndexGsiPk;

  @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "UserNoteIndex"))
  private String userNoteIndexGsiSk;

  private String userId;

  // The enhanced client ignores explicit NUL values when reading, so the transformTo in the
  // converter is never
  // triggered. So we have to initialize this here but its also set in the NoteEntityMapper.
  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalInstantConverter.class))
  private Optional<Instant> lastFormattedAt = Optional.empty();

  public Map<String, String> getContent() {
    return Map.of("front", front, "back", back);
  }
}

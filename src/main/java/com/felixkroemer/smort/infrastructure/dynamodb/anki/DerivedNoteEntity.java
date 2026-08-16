package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.OptionalInstantConverter;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.NoteKeys;
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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class DerivedNoteEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private Long noteId;
  private String front;
  private String back;

  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalInstantConverter.class))
  private Optional<Instant> lastFormattedAt = Optional.empty();

  public DerivedNoteEntity(UUID analysisId, Long noteId, String front, String back) {
    this.noteId = noteId;
    this.front = front;
    this.back = back;
    this.pk = AnalysisKeys.analysisPk(analysisId);
    this.sk = NoteKeys.noteSk(noteId);
  }
  
  public Map<String, String> getContent() {
    return Map.of("front", front, "back", back);
  }
}

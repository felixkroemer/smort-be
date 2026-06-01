package com.felixkroemer.smort.infrastructure.dynamodb.anki;

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
public class DerivedNoteEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private UUID analysisId;
  private Long noteId;
  private String front;
  private String back;

  public DerivedNoteEntity(UUID analysisId, Long noteId, String front, String back) {
    this.analysisId = analysisId;
    this.noteId = noteId;
    this.front = front;
    this.back = back;
    this.pk = AnkiKeys.pk(analysisId);
    this.sk = AnkiKeys.derivedNoteSk(noteId);
  }
}

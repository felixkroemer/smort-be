package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.OptionalStringConverter;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.MetaKeys;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class AnalysisMetaEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  private String dbPath;
  private Long deckId;
  private int noteCount;
  private String deckName;
  private AnalysisStatus status;
  private Instant createdAt;
  private Instant updatedAt;

  @Getter(onMethod_ = @DynamoDbConvertedBy(OptionalStringConverter.class))
  private Optional<String> formatInstructions = Optional.empty();

  public AnalysisMetaEntity(UUID analysisId, AnalysisStatus status) {
    this.pk = AnalysisKeys.analysisPk(analysisId);
    this.sk = MetaKeys.metaSk();
    this.status = status;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getAnalysisId() {
    return UUID.fromString(pk.substring("ANALYSIS#".length()));
  }
}

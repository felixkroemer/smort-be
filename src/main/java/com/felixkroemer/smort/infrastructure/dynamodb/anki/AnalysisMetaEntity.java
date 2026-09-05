package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import com.felixkroemer.smort.domain.common.FormattingMode;
import com.felixkroemer.smort.domain.user.SystemFormattingTemplate;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.MetaKeys;
import java.time.Instant;
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

  @Getter(onMethod_ = @DynamoDbSecondaryPartitionKey(indexNames = "UserAnalysisIndex"))
  private String userAnalysisIndexGsiPk;

  @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "UserAnalysisIndex"))
  private String userAnalysisIndexGsiSk;

  private String userId;

  private String dbPath;
  private Long deckId;
  private int noteCount;
  private String deckName;
  private AnalysisStatus status;
  private Instant createdAt;
  private Instant updatedAt;

  private FormattingMode formattingMode = FormattingMode.DEFAULT;
  private String templateId = SystemFormattingTemplate.DEFAULT.getId();
  private String formatInstructions = "";

  public AnalysisMetaEntity(UUID analysisId, String userId, AnalysisStatus status) {
    this.pk = AnalysisKeys.analysisPk(analysisId);
    this.sk = MetaKeys.metaSk();
    this.userAnalysisIndexGsiPk = AnalysisKeys.userAnalysisIndexGsiPk(userId);
    this.userAnalysisIndexGsiSk = MetaKeys.userAnalysisIndexGsiSk(analysisId);
    this.userId = userId;
    this.status = status;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getAnalysisId() {
    return UUID.fromString(pk.substring("ANALYSIS#".length()));
  }
}

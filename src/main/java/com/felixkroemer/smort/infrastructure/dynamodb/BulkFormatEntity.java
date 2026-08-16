package com.felixkroemer.smort.infrastructure.dynamodb;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public abstract class BulkFormatEntity {

  public abstract String getPk();

  @Getter(onMethod_ = @DynamoDbSortKey)
  protected String sk;

  @Getter(onMethod_ = @DynamoDbSecondaryPartitionKey(indexNames = "StatusBulkFormatIndex"))
  private String statusBulkFormatIndexGsiPk;

  @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "StatusBulkFormatIndex"))
  private String statusBulkFormatIndexGsiSk;

  protected BulkFormatStatus status;
  protected Instant createdAt;
  protected Instant lastUpdatedAt;
  private int totalNotes;
  private int completedNotes;
  protected int attempts;
  protected boolean reformatAlreadyFormatted;

  public void setStatus(BulkFormatStatus status) {
    this.status = status;
    updateGsiKeys();
  }

  protected void updateGsiKeys() {
    this.statusBulkFormatIndexGsiPk = status.name();
    this.statusBulkFormatIndexGsiSk = Instant.now().toString();
  }
}

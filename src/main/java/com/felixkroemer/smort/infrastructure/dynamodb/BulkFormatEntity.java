package com.felixkroemer.smort.infrastructure.dynamodb;

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
public abstract class BulkFormatEntity {

  @Getter(onMethod_ = @DynamoDbPartitionKey)
  private String pk;

  @Getter(onMethod_ = @DynamoDbSortKey)
  private String sk;

  @Getter(onMethod_ = @DynamoDbSecondaryPartitionKey(indexNames = "StatusBulkFormatIndex"))
  private String statusBulkFormatIndexGsiPk;

  @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = "StatusBulkFormatIndex"))
  private String statusBulkFormatIndexGsiSk;

  private BulkFormatStatus status;
  private Instant createdAt;
  private Instant lastUpdatedAt;
  private int totalNotes;
  private int completedNotes;
  private int attempts;
  private boolean reformatAlreadyFormatted;

  public abstract UUID getOwnerId();

  protected void initialize(String pk, String sk, boolean reformatAlreadyFormatted) {
    this.pk = pk;
    this.sk = sk;
    this.status = BulkFormatStatus.PENDING;
    this.createdAt = Instant.now();
    this.lastUpdatedAt = Instant.now();
    this.attempts = 0;
    this.reformatAlreadyFormatted = reformatAlreadyFormatted;
    updateGsiKeys();
  }

  public void setStatus(BulkFormatStatus status) {
    this.status = status;
    updateGsiKeys();
  }

  private void updateGsiKeys() {
    this.statusBulkFormatIndexGsiPk = status.name();
    this.statusBulkFormatIndexGsiSk = Instant.now().toString();
  }
}

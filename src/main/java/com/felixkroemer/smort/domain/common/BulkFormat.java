package com.felixkroemer.smort.domain.common;

import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkFormat {
  private BulkFormatStatus status;
  private Instant createdAt;
  private Instant lastUpdatedAt;
  private int totalNotes;
  private int completedNotes;
  private int attempts;
}

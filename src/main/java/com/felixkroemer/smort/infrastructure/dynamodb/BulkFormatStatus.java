package com.felixkroemer.smort.infrastructure.dynamodb;

public enum BulkFormatStatus {
  PENDING,
  IN_PROGRESS,
  WAITING_RETRY,
  COMPLETED,
  FAILED,
  CANCELLED
}

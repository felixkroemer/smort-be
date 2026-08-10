package com.felixkroemer.smort.infrastructure.dynamodb.anki;

public enum BulkFormatStatus {
  PENDING,
  IN_PROGRESS,
  WAITING_RETRY,
  COMPLETED,
  FAILED
}

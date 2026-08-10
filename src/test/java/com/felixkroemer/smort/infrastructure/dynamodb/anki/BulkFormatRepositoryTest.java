package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

class BulkFormatRepositoryTest {

  private DynamoDbIndex<BulkFormatEntity> statusBulkFormatIndex;
  private BulkFormatRepository repository;

  @BeforeEach
  void setUp() {
    DynamoDbTable<BulkFormatEntity> table = mock(DynamoDbTable.class);
    statusBulkFormatIndex = mock(DynamoDbIndex.class);
    repository = new BulkFormatRepository(table, statusBulkFormatIndex);
  }

  @Test
  void findAllActiveCombinesInProgressAndWaitingRetry() {
    var inProgress = new BulkFormatEntity(UUID.randomUUID());
    inProgress.setStatus(BulkFormatStatus.IN_PROGRESS);
    var waitingRetry = new BulkFormatEntity(UUID.randomUUID());
    waitingRetry.setStatus(BulkFormatStatus.WAITING_RETRY);

    when(statusBulkFormatIndex.query(any(QueryEnhancedRequest.class)))
        .thenReturn(pageOf(inProgress), pageOf(waitingRetry));

    var result = repository.findAllActive();

    assertThat(result).containsExactlyInAnyOrder(inProgress, waitingRetry);
    verify(statusBulkFormatIndex, times(2)).query(any(QueryEnhancedRequest.class));
  }

  private PageIterable<BulkFormatEntity> pageOf(BulkFormatEntity... items) {
    Page<BulkFormatEntity> page = Page.create(List.of(items));
    return () -> List.of(page).iterator();
  }
}

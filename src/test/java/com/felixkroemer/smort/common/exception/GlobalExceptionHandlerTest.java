package com.felixkroemer.smort.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void baseSmortExceptionDefaultsToInternalServerErrorAndErrorSeverity() {
    var ex = new SmortException("Boom: {}", 42);

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(ex.getSeverity()).isEqualTo(LogSeverity.ERROR);

    ResponseEntity<ErrorResponse> response = handler.handle(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("Boom: 42");
  }

  @Test
  void notFoundSubclassMapsToNotFoundWithInfoSeverity() {
    var ex = new DeckNotFoundSmortException(42L);

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ex.getSeverity()).isEqualTo(LogSeverity.INFO);

    ResponseEntity<ErrorResponse> response = handler.handle(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("Deck not found: 42");
  }

  @Test
  void subclassCanOverrideStatusAndSeverity() {
    var ex = new DeckArchivedSmortException(42L);

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.GONE);
    assertThat(ex.getSeverity()).isEqualTo(LogSeverity.WARN);

    ResponseEntity<ErrorResponse> response = handler.handle(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
  }

  @Test
  void withSeverityOverridesSeverityWithoutChangingTypeOrStatus() {
    var ex = new DeckNotFoundSmortException(42L).withSeverity(LogSeverity.ERROR);

    assertThat(ex.getSeverity()).isEqualTo(LogSeverity.ERROR);

    ResponseEntity<ErrorResponse> response = handler.handle(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void causeIsPreservedThroughPatternConstructor() {
    var cause = new IllegalStateException("root cause");
    var ex = new SmortException("Wrapped", cause);

    assertThat(ex.getCause()).isSameAs(cause);
    assertThat(ex.getMessage()).isEqualTo("Wrapped");
  }
}

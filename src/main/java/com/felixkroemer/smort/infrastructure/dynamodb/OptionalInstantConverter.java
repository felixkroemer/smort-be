package com.felixkroemer.smort.infrastructure.dynamodb;

import java.time.Instant;
import java.util.Optional;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class OptionalInstantConverter implements AttributeConverter<Optional<Instant>> {

  @Override
  public AttributeValue transformFrom(Optional<Instant> input) {
    return input
        .map(instant -> AttributeValue.builder().s(instant.toString()).build())
        .orElse(AttributeValue.builder().nul(true).build());
  }

  @Override
  public Optional<Instant> transformTo(AttributeValue input) {
    if (input.nul() != null && input.nul()) return Optional.empty();
    return Optional.ofNullable(input.s()).map(Instant::parse);
  }

  @Override
  public EnhancedType<Optional<Instant>> type() {
    return EnhancedType.optionalOf(Instant.class);
  }

  @Override
  public AttributeValueType attributeValueType() {
    return AttributeValueType.S;
  }
}

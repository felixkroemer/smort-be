package com.felixkroemer.smort.infrastructure.dynamodb;

import java.util.Optional;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class OptionalStringConverter implements AttributeConverter<Optional<String>> {

  @Override
  public AttributeValue transformFrom(Optional<String> input) {
    return input
        .map(s -> AttributeValue.builder().s(s).build())
        .orElse(AttributeValue.builder().nul(true).build());
  }

  @Override
  public Optional<String> transformTo(AttributeValue input) {
    if (input.nul() != null && input.nul()) return Optional.empty();
    return Optional.ofNullable(input.s());
  }

  @Override
  public EnhancedType<Optional<String>> type() {
    return EnhancedType.optionalOf(String.class);
  }

  @Override
  public AttributeValueType attributeValueType() {
    return AttributeValueType.S;
  }
}

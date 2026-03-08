package com.felixkroemer.smort.infrastructure.sqlite.anki;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class FldsConverter implements AttributeConverter<List<String>, String> {

  @Override
  public String convertToDatabaseColumn(List<String> list) {
    return list == null ? null : String.join("\u001f", list);
  }

  @Override
  public List<String> convertToEntityAttribute(String value) {
    return value == null ? List.of() : List.of(value.split("\u001f"));
  }
}

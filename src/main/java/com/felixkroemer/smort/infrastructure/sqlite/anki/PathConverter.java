package com.felixkroemer.smort.infrastructure.sqlite.anki;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.file.Path;

@Converter(autoApply = true)
public class PathConverter implements AttributeConverter<Path, String> {

    @Override
    public String convertToDatabaseColumn(Path path) {
        return path == null ? null : path.toString();
    }

    @Override
    public Path convertToEntityAttribute(String value) {
        return value == null ? null : Path.of(value);
    }
}
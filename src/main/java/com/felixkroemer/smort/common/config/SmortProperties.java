package com.felixkroemer.smort.common.config;

import java.nio.file.Path;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class SmortProperties {
  @Value("${smort.base-data-dir}")
  String baseDir;

  @Value("${smort.anki.analysis.db-directory-name}")
  String ankiAnalysisDbDirectoryName;

  @Value("${smort.anki.analysis.max-db-size}")
  int ankiAnalysisMaxDbSize;

  public Path getAnkiDbDirectory() {
    return Path.of(System.getProperty("user.home"), baseDir, ankiAnalysisDbDirectoryName);
  }
}

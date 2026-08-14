package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Analysis {
  private UUID analysisId;
  private AnalysisStatus status;
  private Long deckId;
  private String deckName;
  private Path dbPath;
  private Instant createdAt;
  private Instant updatedAt;
  private Optional<BulkFormat> bulkFormat = Optional.empty();
  private Optional<String> formatInstructions = Optional.empty();
}

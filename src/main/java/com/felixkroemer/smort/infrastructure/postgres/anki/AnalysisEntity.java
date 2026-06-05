package com.felixkroemer.smort.infrastructure.postgres.anki;

import com.felixkroemer.smort.infrastructure.postgres.common.AuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.nio.file.Path;

@Entity()
@Table(name = "analysis")
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class AnalysisEntity extends AuditEntity {

  Path dbPath;

  Long deckId;

  String deckName;

  @NonNull
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  AnalysisStatus status;
}

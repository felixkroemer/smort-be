package com.felixkroemer.smort.infrastructure.postgres.anki;

import com.felixkroemer.smort.infrastructure.postgres.common.AuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity()
@Table(name = "anki_analysis")
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class AnkiAnalysisEntity extends AuditEntity {

  String dbPath;

  @NonNull
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  AnkiAnalysisStatus status;
}

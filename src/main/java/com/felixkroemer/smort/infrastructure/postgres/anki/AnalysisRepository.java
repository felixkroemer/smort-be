package com.felixkroemer.smort.infrastructure.postgres.anki;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalysisRepository extends JpaRepository<AnkiAnalysisEntity, UUID> {}

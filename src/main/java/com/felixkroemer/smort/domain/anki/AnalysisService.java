package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.anki.mapping.AnalysisEntityMapper;
import com.felixkroemer.smort.domain.common.FormattingMode;
import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

  private final AnalysisMetaRepository analysisMetaRepository;
  private final BulkFormatRepository bulkFormatRepository;

  private final AnalysisEntityMapper analysisEntityMapper;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;

  public Analysis getAnalysis(UUID analysisId) {
    var bulkFormat =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .map(bulkFormatEntityMapper::toBulkFormat);
    return analysisEntityMapper.toAnalysis(getMeta(analysisId), bulkFormat);
  }

  public List<Analysis> getAnalyses() {
    var analysisMetas = analysisMetaRepository.findAnalysisMetasByUserId("default");
    return analysisMetas.stream()
        .map(
            entity -> {
              var bulkFormat =
                  bulkFormatRepository
                      .findBulkFormatByAnalysisId(entity.getAnalysisId())
                      .map(bulkFormatEntityMapper::toBulkFormat);
              return analysisEntityMapper.toAnalysis(entity, bulkFormat);
            })
        .toList();
  }

  public AnalysisSettings updateAnalysisSettings(
      UUID analysisId,
      FormattingMode formattingMode,
      String templateId,
      String formatInstructions) {
    var analysis = getMeta(analysisId);
    if (formattingMode != null) analysis.setFormattingMode(formattingMode);
    if (templateId != null) analysis.setTemplateId(templateId);
    if (formatInstructions != null) analysis.setFormatInstructions(formatInstructions);
    if (formattingMode != null || templateId != null || formatInstructions != null) {
      analysis.setUpdatedAt(Instant.now());
      analysisMetaRepository.save(analysis);
    }
    return analysisEntityMapper.toAnalysisSettings(analysis);
  }

  public void deleteAnalysis(UUID analysisId) {
    var analysis = getMeta(analysisId);
    analysis.setStatus(AnalysisStatus.MARKED_FOR_DELETION);
    analysisMetaRepository.save(analysis);
  }

  public AnalysisMetaEntity getMeta(UUID analysisId) {
    return analysisMetaRepository
        .findAnalysisMetaByAnalysisId(analysisId)
        .orElseThrow(
            () -> new NotFoundException("Could not find analysis by id. id={}", analysisId));
  }
}

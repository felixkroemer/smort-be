package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.domain.note.NoteChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.analysis.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.analysis.DerivedNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteAnalysisService {

  private final AnkiNoteRepository ankiNoteRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final NoteChatService noteChatService;

  public AnkiNoteEntity getNote(UUID analysisId, Long deckId, Long sourceNoteId) {
    return ankiNoteRepository.findById(analysisId, sourceNoteId);
  }

  public Optional<DerivedNoteEntity> getDerivedNote(
      UUID analysisId, Long deckId, Long sourceNoteId) {
    return derivedNoteRepository.findBySourceNoteId(analysisId, deckId, sourceNoteId);
  }

  public List<String> getContent(UUID analysisId, Long deckId, Long sourceNoteId) {
    return getDerivedNote(analysisId, deckId, sourceNoteId)
        .map(DerivedNoteEntity::getFlds)
        .orElseGet(
            () -> {
              var sourceNote = ankiNoteRepository.findById(analysisId, sourceNoteId);
              return sourceNote.getFlds();
            });
  }

  public DerivedNoteEntity formatNote(UUID analysisId, Long deckId, Long sourceNoteId) {
    var content = getContent(analysisId, deckId, sourceNoteId);
    var formattedContent = noteChatService.formatNote(content);

    var derivedNote =
        getDerivedNote(analysisId, deckId, sourceNoteId)
            .orElseGet(
                () -> new DerivedNoteEntity(analysisId, deckId, sourceNoteId, formattedContent));
    derivedNoteRepository.save(derivedNote);

    log.info(
        "Formatted note. analysisId={}, deckId={}, sourceNoteId={}",
        analysisId,
        deckId,
        sourceNoteId);

    return derivedNote;
  }
}

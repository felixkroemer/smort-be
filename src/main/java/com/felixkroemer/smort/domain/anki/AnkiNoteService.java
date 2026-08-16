package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.domain.anki.mapping.DerivedNoteEntityMapper;
import com.felixkroemer.smort.domain.chat.*;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnkiNoteService {

  private final AnkiNoteRepository ankiNoteRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final ChatOrchestrationService chatOrchestrationService;
  private final ChatService chatService;
  private final AnkiNoteTypeService noteTypeService;
  private final AnalysisService analysisService;
  private final DerivedNoteEntityMapper derivedNoteEntityMapper;

  public AnkiNote getNote(UUID analysisId, Long noteId) {
    var note = ankiNoteRepository.findNoteByAnalysisIdAndNoteId(analysisId, noteId);
    var noteTypes = noteTypeService.getNoteTypesByAnalysisId(analysisId);
    return new AnkiNote(
        note.getId(), getFields(note, noteTypes), note.getGuid(), note.getNoteTypeId());
  }

  public Optional<DerivedNoteEntity> getDerivedNote(UUID analysisId, Long noteId) {
    return derivedNoteRepository.findDerivedNotedByAnalysisIdAndNoteId(analysisId, noteId);
  }

  public static Map<String, String> getFields(
      AnkiNoteEntity note, Map<Long, AnkiNoteTypeEntity> noteTypes) {
    var noteType = noteTypes.get(note.getNoteTypeId());
    var noteTypeFieldNames = noteType.getFields();
    return IntStream.range(0, noteTypeFieldNames.size())
        .boxed()
        .collect(Collectors.toMap(noteTypeFieldNames::get, note.getFlds()::get));
  }

  public Map<String, String> getContent(UUID analysisId, Long noteId) {
    var note = getNote(analysisId, noteId);
    return getDerivedNote(analysisId, noteId)
        .map(DerivedNoteEntity::getContent)
        .orElseGet(note::getContent);
  }

  public DerivedNoteEntity formatNote(UUID analysisId, Long noteId) {
    var analysis = analysisService.getAnalysis(analysisId);
    var content = getContent(analysisId, noteId);
    var noteSchema = chatService.formatNote(content, analysis.getFormatInstructions());

    var derivedNote =
        getDerivedNote(analysisId, noteId)
            .map(
                d -> {
                  d.setFront(noteSchema.front());
                  d.setBack(noteSchema.back());
                  d.setLastFormattedAt(Optional.of(Instant.now()));
                  return d;
                })
            .orElseGet(
                () -> derivedNoteEntityMapper.toDerivedNoteEntity(analysisId, noteId, noteSchema));
    derivedNoteRepository.save(derivedNote);

    log.info("Formatted note. analysisId={}, noteId={}", analysisId, noteId);

    return derivedNote;
  }

  public List<ChatMessageEntity> chat(UUID analysisId, Long noteId, String message) {
    var content = getContent(analysisId, noteId);

    return chatOrchestrationService.chat(
        content,
        AnalysisKeys.analysisPk(analysisId),
        noteId,
        message,
        (tx, front, back) -> {
          derivedNoteRepository.saveInTx(
              tx,
              derivedNoteEntityMapper.toDerivedNoteEntity(
                  analysisId, noteId, new NoteSchema(front, back)));
        });
  }
}

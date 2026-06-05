package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.domain.chat.*;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageResponseEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
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
public class AnkiNoteAnalysisService {

  private final AnkiNoteRepository ankiNoteRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final ChatOrchestrationService chatOrchestrationService;
  private final ChatService chatService;
  private final AnkiNoteTypeService noteTypeService;

  public AnalysisNote getNote(UUID analysisId, Long noteId) {
    var note = ankiNoteRepository.findNoteByAnalysisIdAndNoteId(analysisId, noteId);
    var noteTypes = noteTypeService.getNoteTypesByAnalysisId(analysisId);
    var noteType = noteTypes.get(note.getNoteTypeId());
    var noteTypeFieldNames = noteType.getFields();
    var fields =
        IntStream.range(0, noteTypeFieldNames.size())
            .boxed()
            .collect(Collectors.toMap(noteTypeFieldNames::get, note.getFlds()::get));
    return new AnalysisNote(note.getId(), fields, note.getGuid(), note.getNoteTypeId());
  }

  public Optional<DerivedNoteEntity> getDerivedNote(UUID analysisId, Long noteId) {
    return derivedNoteRepository.finDerivedNotedByAnalysisIdAndNoteId(analysisId, noteId);
  }

  public Map<String, String> getContent(UUID analysisId, Long noteId) {
    return getDerivedNote(analysisId, noteId)
        .map(derivedNote -> Map.of("front", derivedNote.getFront(), "back", derivedNote.getBack()))
        .orElseGet(
            () -> {
              var note = this.getNote(analysisId, noteId);
              return note.getFlds();
            });
  }

  public DerivedNoteEntity formatNote(UUID analysisId, Long noteId) {
    var content = getContent(analysisId, noteId);
    var noteSchema = chatService.formatNote(content);

    var derivedNote =
        getDerivedNote(analysisId, noteId)
            .map(
                d -> {
                  d.setFront(noteSchema.getFront());
                  d.setBack(noteSchema.getBack());
                  return d;
                })
            .orElseGet(
                () ->
                    new DerivedNoteEntity(
                        analysisId, noteId, noteSchema.getFront(), noteSchema.getBack()));
    derivedNoteRepository.save(derivedNote);

    log.info("Formatted note. analysisId={}, noteId={}", analysisId, noteId);

    return derivedNote;
  }

  public List<ChatMessageResponseEntity> chat(UUID analysisId, Long noteId, String message) {
    var content = getContent(analysisId, noteId);

    return chatOrchestrationService.chat(
        content,
        AnalysisKeys.analysisPk(analysisId),
        noteId,
        message,
        (tx, front, back) -> {
          derivedNoteRepository.saveInTx(
              tx, new DerivedNoteEntity(analysisId, noteId, front, back));
        });
  }
}

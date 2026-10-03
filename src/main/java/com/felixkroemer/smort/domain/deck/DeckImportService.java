package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.application.deck.dto.NoteTypeTemplate;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.domain.anki.AnkiNote;
import com.felixkroemer.smort.domain.anki.AnkiNoteService;
import com.felixkroemer.smort.domain.anki.AnkiNoteTypeService;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.domain.deck.mapping.DeckEntityMapper;
import com.felixkroemer.smort.domain.deck.mapping.NoteEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeckImportService {

  private static final Pattern FIELD_PATTERN = Pattern.compile("\\$\\{([A-Za-z_]+)}");

  private final AnalysisService analysisService;
  private final AnkiNoteService ankiNoteService;
  private final AnkiNoteTypeService ankiNoteTypeService;
  private final DeckRepository deckRepository;
  private final NoteEntityMapper noteEntityMapper;
  private final DeckEntityMapper deckEntityMapper;

  // TODO: clean up possible failed imports based on status and time passed
  public Deck importDeck(UUID analysisId, Map<String, NoteTypeTemplate> templates) {
    var analysis = analysisService.getAnalysis(analysisId);
    var activeJob = analysis.getBulkFormat();
    if (activeJob.isPresent()
        && (activeJob.get().getStatus() == BulkFormatStatus.IN_PROGRESS
            || activeJob.get().getStatus() == BulkFormatStatus.PENDING
            || activeJob.get().getStatus() == BulkFormatStatus.WAITING_RETRY)) {
      throw new SmortException(
          "Cannot import while bulk format is in progress. analysisId={}", analysisId);
    }
    var deck = createDeck(analysis.getDeckName());

    var notes = ankiNoteService.getNotes(analysisId);
    var derivedNotes = ankiNoteService.getDerivedNotes(analysisId);
    var derivedNoteKeys =
        derivedNotes.stream().map(DerivedNoteEntity::getNoteId).collect(Collectors.toSet());
    var unmappedNotes =
        notes.stream()
            .filter(n -> !derivedNoteKeys.contains(n.getId()))
            .collect(Collectors.toList());

    if (!derivedNotes.isEmpty()) {
      handleDerivedNotes(deck.getDeckId(), derivedNotes);
    }
    if (!unmappedNotes.isEmpty()) {
      handleUnmappedNotes(deck.getDeckId(), unmappedNotes, analysisId, templates);
    }

    deck.setStatus(DeckStatus.ACTIVE);
    deckRepository.saveDeckMeta(deck);
    return deckEntityMapper.toDeck(deck, Optional.empty(), Optional.empty());
  }

  private DeckMetaEntity createDeck(String deckName) {
    var deck = new DeckMetaEntity(UUID.randomUUID(), deckName, "default");
    deckRepository.saveDeckMeta(deck);
    return deck;
  }

  private void handleDerivedNotes(UUID deckId, List<DerivedNoteEntity> derivedNotes) {
    derivedNotes.stream()
        .map(
            d ->
                noteEntityMapper.toNoteEntity(
                    deckId, UUID.randomUUID(), new NoteSchema(d.getFront(), d.getBack()), "default"))
        .forEach(deckRepository::saveNote);
  }

  private void handleUnmappedNotes(
      UUID deckId,
      List<AnkiNote> unmappedNotes,
      UUID analysisId,
      Map<String, NoteTypeTemplate> templates) {
    var noteTypes = ankiNoteTypeService.getNoteTypesByAnalysisId(analysisId);

    unmappedNotes.stream()
        .map(note -> toNoteEntity(deckId, note, noteTypes, templates))
        .forEach(deckRepository::saveNote);
  }

  private NoteEntity toNoteEntity(
      UUID deckId,
      AnkiNote ankiNote,
      Map<Long, AnkiNoteTypeEntity> noteTypes,
      Map<String, NoteTypeTemplate> templates) {
    var noteType = noteTypes.get(ankiNote.getNoteTypeId());
    var template = templates.get(noteType.getName());
    if (template == null) {
      throw new SmortException(
          "No template provided for note type. noteType={}", noteType.getName());
    }
    var schema =
        getNoteSchema(ankiNote.getContent(), template.frontTemplate(), template.backTemplate());
    return noteEntityMapper.toNoteEntity(deckId, UUID.randomUUID(), schema, "default");
  }

  private NoteSchema getNoteSchema(
      Map<String, String> fields, String frontTemplate, String backTemplate) {
    var replacer = buildReplacer(fields);
    return new NoteSchema(
        FIELD_PATTERN.matcher(frontTemplate).replaceAll(replacer),
        FIELD_PATTERN.matcher(backTemplate).replaceAll(replacer));
  }

  private Function<MatchResult, String> buildReplacer(Map<String, String> fields) {
    return m -> {
      String key = m.group(1);
      if (!fields.containsKey(key)) {
        throw new SmortException("Unknown field: " + key);
      }
      return Matcher.quoteReplacement(fields.get(key));
    };
  }
}
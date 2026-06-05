package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.application.deck.dto.NoteTypeTemplate;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.domain.anki.AnkiNote;
import com.felixkroemer.smort.domain.anki.AnkiNoteTypeService;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
import java.util.List;
import java.util.Map;
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
public class DeckService {

  private static final Pattern FIELD_PATTERN = Pattern.compile("\\$\\{([A-Za-z_]+)}");

  private final AnalysisService analysisService;
  private final AnkiNoteTypeService ankiNoteTypeService;
  private final DeckRepository deckRepository;

  // TODO: clean up possible failed imports based on status and time passed
  public void importDeck(UUID analysisId, Map<String, NoteTypeTemplate> templates) {
    var analysis = analysisService.getAnalysis(analysisId);
    var deck = createDeck(analysis.getDeckName());

    var notes = analysisService.getNotes(analysisId);
    var derivedNotes = analysisService.getDerivedNotes(analysisId);
    var derivedNoteKeys =
        derivedNotes.stream().map(DerivedNoteEntity::getNoteId).collect(Collectors.toSet());
    var unmappedNotes =
        notes.stream()
            .filter(n -> !derivedNoteKeys.contains(n.getId()))
            .collect(Collectors.toList());

    handleDerivedNotes(deck.getDeckId(), derivedNotes);
    handleUnmappedNotes(deck.getDeckId(), unmappedNotes, analysisId, templates);

    deck.setStatus(DeckStatus.ACTIVE);
    deckRepository.saveDeckMeta(deck);
  }

  private DeckMetaEntity createDeck(String deckName) {
    var deck = new DeckMetaEntity(UUID.randomUUID(), deckName, "default");
    deckRepository.saveDeckMeta(deck);
    return deck;
  }

  private void handleDerivedNotes(UUID deckId, List<DerivedNoteEntity> derivedNotes) {
    derivedNotes.stream()
        .map(d -> new NoteEntity(deckId, UUID.randomUUID(), d.getFront(), d.getBack()))
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
        getNoteSchema(ankiNote.getFlds(), template.frontTemplate(), template.backTemplate());
    return new NoteEntity(deckId, UUID.randomUUID(), schema.front(), schema.back());
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

  public List<DeckMetaEntity> getDecks() {
    return deckRepository.findDeckMetasByUserId("default");
  }

  public void deleteDeck(UUID deckId) {
    var deck =
        deckRepository
            .findDeckMetaByDeckId(deckId)
            .orElseThrow(() -> new SmortException("Could not find deck. deckId={}", deckId));
    deck.setStatus(DeckStatus.MARKED_FOR_DELETION);
    deckRepository.saveDeckMeta(deck);
  }

  public void deleteNote(UUID deckId, UUID noteId) {
    deckRepository.deleteNoteByDeckIdAndNoteId(deckId, noteId);
  }
}

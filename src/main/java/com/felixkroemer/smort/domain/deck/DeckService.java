package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.application.deck.dto.NoteTypeTemplate;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.domain.anki.AnkiNote;
import com.felixkroemer.smort.domain.anki.AnkiNoteTypeService;
import com.felixkroemer.smort.domain.chat.ChatMessage;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.domain.chat.DeckChatContext;
import com.felixkroemer.smort.domain.chat.DeckChatToolType;
import com.felixkroemer.smort.domain.chat.DraftNoteToolChatMessage;
import com.felixkroemer.smort.domain.chat.ToolCallHandler;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.domain.deck.mapping.DeckEntityMapper;
import com.felixkroemer.smort.domain.deck.mapping.DraftNoteEntityMapper;
import com.felixkroemer.smort.domain.deck.mapping.NoteEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@Service
@RequiredArgsConstructor
public class DeckService {

  private static final Pattern FIELD_PATTERN = Pattern.compile("\\$\\{([A-Za-z_]+)}");

  private final AnalysisService analysisService;
  private final AnkiNoteTypeService ankiNoteTypeService;
  private final ChatOrchestrationService chatOrchestrationService;
  private final ChatRepository chatRepository;
  private final DeckRepository deckRepository;
  private final DraftNoteRepository draftNoteRepository;
  private final NoteEntityMapper noteEntityMapper;
  private final BulkFormatRepository bulkFormatRepository;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;
  private final DeckEntityMapper deckEntityMapper;
  private final DraftNoteEntityMapper draftNoteEntityMapper;
  private final DynamoDbEnhancedClient enhancedClient;

  public List<NoteEntity> getNotes(UUID deckId) {
    return deckRepository.findNotesByDeckId(deckId);
  }

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

    var notes = analysisService.getNotes(analysisId);
    var derivedNotes = analysisService.getDerivedNotes(analysisId);
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
                    deckId, UUID.randomUUID(), new NoteSchema(d.getFront(), d.getBack())))
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
    return noteEntityMapper.toNoteEntity(deckId, UUID.randomUUID(), schema);
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

  public List<Deck> getDecks() {
    return deckRepository.findDeckMetasByUserId("default").stream()
        .map(
            entity ->
                deckEntityMapper.toDeck(
                    entity,
                    bulkFormatRepository
                        .findBulkFormatByDeckId(entity.getDeckId())
                        .map(bulkFormatEntityMapper::toBulkFormat),
                    draftNoteRepository
                        .findDraftNote(entity.getDeckId())
                        .map(draftNoteEntityMapper::toDraftNote)))
        .toList();
  }

  public void deleteDeck(UUID deckId) {
    var deck = getMeta(deckId);
    deck.setStatus(DeckStatus.MARKED_FOR_DELETION);
    deckRepository.saveDeckMeta(deck);
  }

  public DeckSettings getDeckSettings(UUID deckId) {
    return new DeckSettings(getMeta(deckId).getFormatInstructions());
  }

  public DeckSettings updateDeckSettings(UUID deckId, Optional<String> formatInstructions) {
    var deck = getMeta(deckId);
    if (formatInstructions != null) {
      deck.setFormatInstructions(formatInstructions);
      deckRepository.saveDeckMeta(deck);
    }
    return new DeckSettings(deck.getFormatInstructions());
  }

  public void deleteNote(UUID deckId, UUID noteId) {
    deckRepository.deleteNoteByDeckIdAndNoteId(deckId, noteId);
  }

  public List<ChatMessageEntity> chat(UUID deckId, String message) {
    var deck = getMeta(deckId);

    var formatInstructions = getDeckSettings(deckId).formatInstructions();

    var notes =
        deckRepository.findNotesByDeckId(deckId).stream().map(NoteEntity::getFront).toList();

    var draft =
        draftNoteRepository
            .findDraftNote(deckId)
            .map(d -> new NoteSchema(d.getFront(), d.getBack()));

    var ctx = new DeckChatContext(deckId, deck.getName(), notes, draft);

    Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
        Map.of(
            DraftNoteToolChatMessage.class,
            (tx, toolCall) -> {
              var m = (DraftNoteToolChatMessage) toolCall;
              draftNoteRepository.saveInTx(tx, new DraftNoteEntity(deckId, m.front(), m.back()));
            });

    return chatOrchestrationService.deckChat(
        DeckKeys.deckPk(deckId), ctx, message, formatInstructions, toolHandlers);
  }

  public List<ChatMessageEntity> getChat(UUID deckId) {
    return chatOrchestrationService.getChat(DeckKeys.deckPk(deckId), deckId);
  }

  public void clearChat(UUID deckId) {
    chatRepository.deleteChat(DeckKeys.deckPk(deckId), deckId);
  }

  public DraftNoteEntity getDraftNote(UUID deckId) {
    return draftNoteRepository
        .findDraftNote(deckId)
        .orElseThrow(() -> new NotFoundException("Could not find draft note. deckId={}", deckId));
  }

  public void clearDraftNote(UUID deckId) {
    draftNoteRepository.delete(deckId);
  }

  public List<ChatMessageEntity> storeDraftNote(UUID deckId) {
    var draft = getDraftNote(deckId);

    var note =
        noteEntityMapper.toNoteEntity(
            deckId, UUID.randomUUID(), new NoteSchema(draft.getFront(), draft.getBack()));

    var addNoteMessageEntity =
        ChatMessageEntity.toolCall(
            DeckKeys.deckPk(deckId),
            deckId,
            Optional.empty(),
            UUID.randomUUID().toString(),
            Optional.empty(),
            UUID.randomUUID().toString(),
            DeckChatToolType.ADD_NOTE.name(),
            Optional.empty(),
            true,
            Map.of("front", draft.getFront(), "back", draft.getBack()));

    var txBuilder = TransactWriteItemsEnhancedRequest.builder();
    deckRepository.saveNoteInTx(txBuilder, note);
    draftNoteRepository.deleteInTxIfPresent(txBuilder, deckId);
    chatRepository.saveInTx(txBuilder, addNoteMessageEntity);
    enhancedClient.transactWriteItems(txBuilder.build());

    return List.of(addNoteMessageEntity);
  }

  private DeckMetaEntity getMeta(UUID deckId) {
    return deckRepository
        .findDeckMetaByDeckId(deckId)
        .orElseThrow(() -> new NotFoundException("Could not find deck by id. deckId={}", deckId));
  }
}

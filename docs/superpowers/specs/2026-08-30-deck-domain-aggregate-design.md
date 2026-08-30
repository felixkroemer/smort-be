# Deck Domain Aggregate Design

## Goal

Mirror the `Analysis` domain aggregate pattern for `Deck`. Currently `DeckService`
returns the infrastructure entity `DeckMetaEntity` directly from its public API.
Introduce a domain `Deck` class that combines the deck meta entity, the deck's bulk
format, and the deck's draft note, and stop leaking `DeckMetaEntity` out of the service.

## Background: the Analysis pattern

`Analysis` (`domain/anki/Analysis.java`) is a domain POJO combining:

- fields from `AnalysisMetaEntity` (meta entity)
- `Optional<BulkFormat>` (domain object mapped from `AnalysisBulkFormatEntity`)

`AnalysisService` assembles it:

```java
public Analysis getAnalysis(UUID analysisId) {
  var bulkFormat =
      bulkFormatRepository
          .findBulkFormatByAnalysisId(analysisId)
          .map(bulkFormatEntityMapper::toBulkFormat);
  return analysisEntityMapper.toAnalysis(getMeta(analysisId), bulkFormat);
}
```

Mapping lives in MapStruct mappers:

- `AnalysisEntityMapper.toAnalysis(AnalysisMetaEntity, Optional<BulkFormat>)`
- `BulkFormatEntityMapper.toBulkFormat(BulkFormatEntity)` (separate mapper)
- private `AnalysisMetaEntity getMeta(UUID)` throws `NotFoundException` when absent

## Design

### 1. New domain class: `Deck`

`domain/deck/Deck.java` — Lombok POJO mirroring `Analysis`. Excludes persistence
fields (`pk`, `sk`, `userDeckIndexGsi*`), like `Analysis` excludes its keys.

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Deck {
  private UUID deckId;
  private String name;
  private String userId;
  private DeckStatus status;
  private Optional<BulkFormat> bulkFormat = Optional.empty();
  private Optional<DraftNote> draftNote = Optional.empty();
}
```

### 2. New domain record: `DraftNote`

`domain/deck/DraftNote.java`

```java
public record DraftNote(String front, String back) {}
```

### 3. New mapper: `DraftNoteEntityMapper`

`domain/deck/mapping/DraftNoteEntityMapper.java` — separate mapper like
`BulkFormatEntityMapper`.

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DraftNoteEntityMapper {
  DraftNote toDraftNote(DraftNoteEntity entity);
}
```

### 4. New mapper: `DeckEntityMapper`

`domain/deck/mapping/DeckEntityMapper.java` — combines like `AnalysisEntityMapper`.

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DeckEntityMapper {
  Deck toDeck(DeckMetaEntity meta, Optional<BulkFormat> bulkFormat, Optional<DraftNote> draftNote);
}
```

### 5. `DeckService` changes

Mirror `AnalysisService`:

- **`importDeck`** now returns `Deck`. Logic unchanged; after final save the entity is
  mapped to domain. No bulk format or draft note exists at import time, so both are empty:
  `return deckEntityMapper.toDeck(deck, Optional.empty(), Optional.empty());`
- **`getDecks`** now returns `List<Deck>`, parallel to `getAnalyses`:
  ```java
  public List<Deck> getDecks() {
    return deckRepository.findDeckMetasByUserId("default").stream()
        .map(entity -> deckEntityMapper.toDeck(entity,
            bulkFormatRepository.findBulkFormatByDeckId(entity.getDeckId())
                .map(bulkFormatEntityMapper::toBulkFormat),
            draftNoteRepository.findDraftNote(entity.getDeckId())
                .map(draftNoteEntityMapper::toDraftNote)))
        .toList();
  }
  ```
- **New private `getMeta(UUID deckId)`** like `AnalysisService.getMeta`:
  ```java
  private DeckMetaEntity getMeta(UUID deckId) {
    return deckRepository.findDeckMetaByDeckId(deckId)
        .orElseThrow(() -> new NotFoundException("Could not find deck by id. deckId={}", deckId));
  }
  ```
- **`deleteDeck`** and **`chat`** use `getMeta(deckId)` instead of inline
  `findDeckMetaByDeckId(...).orElseThrow(...)`.
- `createDeck` stays private, still returns `DeckMetaEntity` (internal persistence only).
- No `DeckMetaEntity` leaves the service through its public API.
- New dependencies: `BulkFormatRepository`, `BulkFormatEntityMapper`,
  `DraftNoteEntityMapper`, `DeckEntityMapper`.

### 6. `DeckRestMapper` changes

Switch from `DeckMetaEntity` to `Deck`:

```java
DeckResponse toDeckResponse(Deck deck);
List<DeckResponse> toDeckResponse(List<Deck> decks);
```

Both `DeckController` usages (`importAnalysis`, `getDecks`) now pass a domain `Deck`.
The `DeckMetaEntity` overloads are removed.

### 7. `DeckController` changes

`getDecks()` local renamed only:

```java
var decks = deckService.getDecks();
return deckRestMapper.toDeckResponse(decks);
```

## Files

New:
- `domain/deck/Deck.java`
- `domain/deck/DraftNote.java`
- `domain/deck/mapping/DeckEntityMapper.java`
- `domain/deck/mapping/DraftNoteEntityMapper.java`

Modified:
- `domain/deck/DeckService.java`
- `application/deck/mapping/DeckRestMapper.java`
- `application/deck/DeckController.java`

## Error handling

- Missing deck meta (in `getMeta`, `deleteDeck`, `chat`) → `NotFoundException`, same as
  today.
- Bulk format and draft note are optional; absent values yield `Optional.empty()`.

## Testing

None. Tests are only written when explicitly requested per project convention.

## Out of scope

- Changing `getDraftNote`/`clearDraftNote`/other deck methods to the domain class.
- Refactoring the bulk-format or draft-note repositories.
- Note-layer changes.
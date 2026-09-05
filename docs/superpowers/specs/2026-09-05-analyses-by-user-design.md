# Design: Fetch analyses by user via `UserAnalysisIndex` GSI

Date: 2026-09-05

## Goal

`AnalysisService.getAnalyses()` currently fetches all analyses via a full-table
scan. It should only fetch the analyses of the current user, mirroring how
`DeckService.getDecks()` works via `DeckRepository.findDeckMetasByUserId()`.
Because the codebase has no auth yet, the current user is the hardcoded dummy
user `"default"`, exactly as decks already use.

## Approach

Mirror the deck-by-user pattern. Add a `UserAnalysisIndex` global secondary
index on `AnalysisMetaEntity` (partition key `USER#<userId>`, sort key
`ANALYSIS#<analysisId>`), add a `userId` attribute, query the index by user in
the repository, and switch the service to that query. Remove the now-dead
`findAllAnalysisMetas()` scan.

## Changes

1. **`AnalysisMetaEntity`** — add `userId` field and two GSI-key fields annotated
   for a new `UserAnalysisIndex`:
   - `userAnalysisIndexGsiPk` = `@DynamoDbSecondaryPartitionKey(indexNames = "UserAnalysisIndex")`
   - `userAnalysisIndexGsiSk` = `@DynamoDbSecondarySortKey(indexNames = "UserAnalysisIndex")`
   - Constructor becomes `AnalysisMetaEntity(UUID analysisId, String userId, AnalysisStatus status)`;
     it populates the GSI keys and the `userId`.

2. **`AnalysisKeys`** — add `userAnalysisIndexGsiPk(String userId)` returning
   `"USER#" + userId`.

3. **`MetaKeys`** — add `userAnalysisIndexGsiSk(UUID analysisId)` returning
   `"ANALYSIS#" + analysisId`.

4. **`DynamoDbClientConfig`** — add `@Bean userAnalysisIndex` returning
   `analysisMetaTable.index("UserAnalysisIndex")`.

5. **`AnalysisMetaRepository`** — add `findAnalysisMetasByUserId(String userId)`
   querying the GSI on partition key `USER#<userId>`, filtering out
   `MARKED_FOR_DELETION`. Remove the now-unused `findAllAnalysisMetas()` scan.

6. **`AnalysisService`** — `createAnalysis()` passes `"default"` as the userId;
   `getAnalyses()` calls `findAnalysisMetasByUserId("default")`.

## Data flow

`getAnalyses()` → `findAnalysisMetasByUserId("default")` → GSI query → maps each
meta to `Analysis` (bulk-format lookup unchanged) → returns only the current
user's analyses.

## Error handling

Unchanged — no new failure modes beyond the existing query path.

## Testing

No new tests (per AGENTS.md, tests are only written when explicitly requested).
Compilation is owned by the human and skipped in implementation.

## Operational note

The `UserAnalysisIndex` GSI must be provisioned on `common-table` in DynamoDB,
the same way `UserDeckIndex` already exists. This is an external/infra concern,
not part of the code change.
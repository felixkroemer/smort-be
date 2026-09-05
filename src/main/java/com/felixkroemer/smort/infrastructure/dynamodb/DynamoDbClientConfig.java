package com.felixkroemer.smort.infrastructure.dynamodb;

import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisMetaEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckBulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserFormattingTemplateEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.user.UserSettingsEntity;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbClientConfig {

  private static final String COMMON_TABLE_NAME = "common-table";

  @Bean
  @Profile("local")
  DynamoDbEnhancedClient createLocalDynamoDbClient() {
    DynamoDbClient dynamoDbClient =
        DynamoDbClient.builder()
            .endpointOverride(URI.create("http://localhost:8000"))
            .region(Region.EU_CENTRAL_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
            .build();

    return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
  }

  @Bean
  public DynamoDbTable<DerivedNoteEntity> derivedNoteTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(DerivedNoteEntity.class));
  }

  @Bean
  public DynamoDbTable<NoteEntity> noteTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(NoteEntity.class));
  }

  @Bean
  public DynamoDbTable<DraftNoteEntity> draftNoteTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(DraftNoteEntity.class));
  }

  @Bean
  DynamoDbTable<DeckMetaEntity> deckMetaTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(DeckMetaEntity.class));
  }

  @Bean
  DynamoDbTable<AnalysisBulkFormatEntity> analysisBulkFormatTable(
      DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(
        COMMON_TABLE_NAME, TableSchema.fromBean(AnalysisBulkFormatEntity.class));
  }

  @Bean
  DynamoDbTable<DeckBulkFormatEntity> deckBulkFormatTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(
        COMMON_TABLE_NAME, TableSchema.fromBean(DeckBulkFormatEntity.class));
  }

  @Bean
  DynamoDbTable<AnalysisMetaEntity> analysisMetaTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(AnalysisMetaEntity.class));
  }

  @Bean
  DynamoDbTable<UserSettingsEntity> userSettingsTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(UserSettingsEntity.class));
  }

  @Bean
  DynamoDbTable<UserFormattingTemplateEntity> userFormattingTemplateTable(
      DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(
        COMMON_TABLE_NAME, TableSchema.fromBean(UserFormattingTemplateEntity.class));
  }

  @Bean
  DynamoDbIndex<DeckMetaEntity> userDeckIndex(DynamoDbTable<DeckMetaEntity> deckMetaTable) {
    return deckMetaTable.index("UserDeckIndex");
  }

  @Bean
  DynamoDbIndex<NoteEntity> userNoteIndex(DynamoDbTable<NoteEntity> noteTable) {
    return noteTable.index("UserNoteIndex");
  }

  @Bean
  DynamoDbIndex<AnalysisMetaEntity> userAnalysisIndex(
      DynamoDbTable<AnalysisMetaEntity> analysisMetaTable) {
    return analysisMetaTable.index("UserAnalysisIndex");
  }

  @Bean
  DynamoDbIndex<AnalysisBulkFormatEntity> statusBulkFormatIndex(
      DynamoDbTable<AnalysisBulkFormatEntity> analysisBulkFormatTable) {
    return analysisBulkFormatTable.index("StatusBulkFormatIndex");
  }

  @Bean
  DynamoDbIndex<DeckBulkFormatEntity> statusDeckBulkFormatIndex(
      DynamoDbTable<DeckBulkFormatEntity> deckBulkFormatTable) {
    return deckBulkFormatTable.index("StatusBulkFormatIndex");
  }

  @Bean
  public DynamoDbTable<ChatMessageEntity> chatMessageTable(DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(COMMON_TABLE_NAME, TableSchema.fromBean(ChatMessageEntity.class));
  }
}

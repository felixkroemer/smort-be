package com.felixkroemer.smort.infrastructure.dynamodb;

import com.felixkroemer.smort.infrastructure.dynamodb.anki.ChatMessageResponseEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbClientConfig {

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
    return enhancedClient.table("derived-notes", TableSchema.fromBean(DerivedNoteEntity.class));
  }

  @Bean
  public DynamoDbTable<ChatMessageResponseEntity> chatMessageResponseTable(
      DynamoDbEnhancedClient enhancedClient) {
    return enhancedClient.table(
        "derived-notes", TableSchema.fromBean(ChatMessageResponseEntity.class));
  }
}

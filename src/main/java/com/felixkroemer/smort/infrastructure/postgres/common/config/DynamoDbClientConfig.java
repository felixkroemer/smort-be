package com.felixkroemer.smort.infrastructure.postgres.common.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbClientConfig {

  @Bean
  DynamoDbClient createLocalDynamoDbClient() {
    return DynamoDbClient.builder()
        .endpointOverride(URI.create("http://localhost:8000"))
        .region(Region.EU_CENTRAL_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
        .build();
  }
}

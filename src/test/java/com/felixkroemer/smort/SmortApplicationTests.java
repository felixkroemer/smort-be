package com.felixkroemer.smort;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(SmortApplicationTests.DynamoDbTestConfig.class)
class SmortApplicationTests {

  @MockitoBean OpenAIClient openAIClient;

  @TestConfiguration
  static class DynamoDbTestConfig {

    @Bean
    DynamoDbEnhancedClient dynamoDbEnhancedClient() {
      DynamoDbEnhancedClient client = mock(DynamoDbEnhancedClient.class);
      @SuppressWarnings({"rawtypes", "unchecked"})
      DynamoDbTable<Object> table = mock(DynamoDbTable.class);
      when(client.table(anyString(), any(TableSchema.class))).thenReturn(table);
      when(table.index(anyString())).thenReturn(mock(DynamoDbIndex.class));
      return client;
    }
  }

  @Test
  void contextLoads() {}
}

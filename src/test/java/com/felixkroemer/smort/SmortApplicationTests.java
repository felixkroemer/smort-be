package com.felixkroemer.smort;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class SmortApplicationTests {

  @MockitoBean OpenAIClient openAIClient;

  @Test
  void contextLoads() {}
}

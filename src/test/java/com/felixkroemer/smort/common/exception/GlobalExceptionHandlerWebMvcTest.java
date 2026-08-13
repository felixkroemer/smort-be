package com.felixkroemer.smort.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerWebMvcTest.TestController.class)
class GlobalExceptionHandlerWebMvcTest {

  @Autowired MockMvc mockMvc;

  @Test
  void thrownSmortExceptionIsHandledByAdvice() throws Exception {
    mockMvc
        .perform(get("/boom"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Deck not found: 42"));
  }

  @RestController
  static class TestController {
    @GetMapping("/boom")
    void boom() {
      throw new DeckNotFoundSmortException(42L);
    }
  }
}

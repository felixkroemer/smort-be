package com.felixkroemer.smort.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmortConfig {

  @Bean
  ObjectMapper createObjectMapper() {
    return new ObjectMapper();
  }
}

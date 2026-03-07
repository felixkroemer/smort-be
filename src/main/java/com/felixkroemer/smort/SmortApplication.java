package com.felixkroemer.smort;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SmortApplication {

  public static void main(String[] args) {
    SpringApplication.run(SmortApplication.class, args);
  }
}

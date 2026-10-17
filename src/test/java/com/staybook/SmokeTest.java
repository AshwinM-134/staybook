package com.staybook;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class SmokeTest {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresSupport.register(registry);
  }

  @Autowired private ApplicationContext context;

  @Test
  void contextLoads() {
    assertNotNull(context);
  }
}

package com.staybook;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Replaces the app clock with one the gates can move. */
@TestConfiguration
class ControllableClockConfig {

  @Bean
  ControllableClock clock() {
    return new ControllableClock(ControllableClock.START);
  }
}

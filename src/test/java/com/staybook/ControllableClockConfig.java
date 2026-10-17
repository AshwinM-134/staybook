package com.staybook;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Primary clock for the gates. The app clock bean stays registered beside it. */
@TestConfiguration
class ControllableClockConfig {

  @Bean
  @Primary
  ControllableClock testClock() {
    return new ControllableClock(ControllableClock.START);
  }
}

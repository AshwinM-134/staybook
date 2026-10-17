package com.staybook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@Import(ControllableClockConfig.class)
@ImportTestcontainers(SharedPostgres.class)
@SpringBootTest
class SmokeTest {

  @Autowired private ApplicationContext context;

  @Autowired private Clock clock;

  @Test
  void theApplicationContextStartsAgainstPostgres() {
    assertNotNull(context);
  }

  @Test
  void injectedClockIsTheTestClock() {
    assertEquals(ControllableClock.START, clock.instant());
  }
}

package com.staybook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TestClockTest {

  @Test
  void appClockIsSystemUtc() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(ClockConfig.class)) {
      assertSame(Clock.systemUTC(), context.getBean(Clock.class));
    }
  }

  @Test
  void nightIsSixtyDaysAfterTheClock() {
    ControllableClock clock = new ControllableClock(ControllableClock.START);
    assertEquals(LocalDate.of(2027, 3, 2), Fixtures.night(clock));
    clock.advance(Duration.ofMinutes(1));
    assertEquals(ControllableClock.START.plus(Duration.ofMinutes(1)), clock.instant());
    assertEquals(LocalDate.of(2027, 3, 2), Fixtures.night(clock));
  }
}

package com.staybook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    assertEquals(LocalDate.of(2099, 3, 2), Fixtures.night(clock));
    clock.advance(Duration.ofMinutes(1));
    assertEquals(ControllableClock.START.plus(Duration.ofMinutes(1)), clock.instant());
    assertEquals(LocalDate.of(2099, 3, 2), Fixtures.night(clock));
  }

  @Test
  void zonedClockTracksTheNextAdvance() {
    ControllableClock clock = new ControllableClock(ControllableClock.START);
    Clock view = clock.withZone(ZoneId.of("America/New_York"));
    assertEquals(ZoneId.of("America/New_York"), view.getZone());
    assertEquals(ControllableClock.START, view.instant());
    clock.advance(Duration.ofMinutes(1));
    assertEquals(ControllableClock.START.plus(Duration.ofMinutes(1)), view.instant());
    assertInstanceOf(ControllableClock.class, view);
    ((ControllableClock) view).advance(Duration.ofMinutes(1));
    assertEquals(ControllableClock.START.plus(Duration.ofMinutes(2)), clock.instant());
    assertSame(clock, clock.withZone(ZoneOffset.UTC));
  }

  @Test
  void concurrentAdvancesDoNotDropTime() throws InterruptedException {
    ControllableClock clock = new ControllableClock(ControllableClock.START);
    int threads = 4;
    int steps = 250;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              await(go);
              for (int step = 0; step < steps; step++) {
                clock.advance(Duration.ofMillis(1));
              }
            });
      }
      ready.await();
      go.countDown();
      pool.shutdown();
      assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }
    assertEquals(ControllableClock.START.plusMillis((long) threads * steps), clock.instant());
  }

  @Test
  void testClockIsPrimaryInEitherRegistrationOrder() {
    assertPrimaryTestClock(ClockConfig.class, ControllableClockConfig.class);
    assertPrimaryTestClock(ControllableClockConfig.class, ClockConfig.class);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  private static void assertPrimaryTestClock(Class<?>... configs) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try (context) {
      context.setAllowBeanDefinitionOverriding(false);
      context.register(configs);
      context.refresh();
      Map<String, Clock> clocks = context.getBeansOfType(Clock.class);
      Clock testClock = clocks.get("testClock");
      assertNotNull(testClock);
      assertEquals(ControllableClock.START, testClock.instant());
      assertSame(Clock.systemUTC(), clocks.get("clock"));
      assertEquals(ControllableClock.START, context.getBean(Clock.class).instant());
    }
  }
}

package com.staybook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("level2")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class Level2ExitGateTest extends ExitGateHarness {

  private static final int OVERBOOK_ATTEMPTS = 100;
  private static final int ROOM_TOTAL = 5;
  private static final int CREATED = 201;
  private static final int CONFLICT = 409;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresSupport.register(registry);
  }

  @Test
  void parallelSameIdempotencyKeyCreatesOneBooking() throws Exception {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate night = LocalDate.of(2026, 12, 20);
    Fixtures.insertInventory(jdbc(), roomTypeId, night, ROOM_TOTAL, 0);
    warmup();
    List<HttpExchange> responses = burst(10, userId, roomTypeId, night, "shared-key");
    long bookingId = read(responses.get(0)).get("id").asLong();
    for (HttpExchange response : responses) {
      assertEquals(201, response.status());
      assertEquals(bookingId, read(response).get("id").asLong());
    }
    assertEquals(1, Fixtures.countBookings(jdbc()));
    assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, night));
  }

  @Test
  void concurrentBookingsDoNotOverbook() throws Exception {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate night = LocalDate.of(2026, 12, 20);
    Fixtures.insertInventory(jdbc(), roomTypeId, night, ROOM_TOTAL, 0);
    warmup();
    int created = 0;
    int conflict = 0;
    int index = 0;
    for (HttpExchange response : burst(OVERBOOK_ATTEMPTS, userId, roomTypeId, night, null)) {
      if (response.status() == CREATED) {
        created++;
      } else if (response.status() == CONFLICT) {
        conflict++;
      } else {
        fail("unexpected status " + response.status() + " for attempt " + index);
      }
      index++;
    }
    assertEquals(ROOM_TOTAL, created);
    assertEquals(OVERBOOK_ATTEMPTS - ROOM_TOTAL, conflict);
    assertEquals(ROOM_TOTAL, Fixtures.reserved(jdbc(), roomTypeId, night));
  }

  private List<HttpExchange> burst(
      int threads, long userId, long roomTypeId, LocalDate night, String sharedKey)
      throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(threads);
    List<Future<HttpExchange>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        String key = sharedKey == null ? "book-" + i : sharedKey;
        futures.add(
            pool.submit(
                () -> {
                  barrier.await(30, TimeUnit.SECONDS);
                  return postBooking(userId, roomTypeId, night, night.plusDays(1), key, false);
                }));
      }
      List<HttpExchange> responses = new ArrayList<>();
      for (Future<HttpExchange> future : futures) {
        responses.add(future.get(30, TimeUnit.SECONDS));
      }
      return responses;
    }
  }
}

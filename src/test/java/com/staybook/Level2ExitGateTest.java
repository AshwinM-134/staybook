package com.staybook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("level2")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class Level2ExitGateTest extends ExitGateSupport {

  private static final int OVERBOOK_ATTEMPTS = 100;
  private static final int ROOM_TOTAL = 5;
  private static final int CREATED = 201;
  private static final int CONFLICT = 409;
  private static final Duration EXPIRY_WINDOW = Duration.ofSeconds(10);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    SharedPostgres.register(registry);
  }

  @Test
  void tenParallelCallsWithOneKeyCreateOneBooking() throws Exception {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate night = LocalDate.of(2026, 12, 20);
    Fixtures.insertInventory(jdbc(), roomTypeId, night, ROOM_TOTAL, 0);
    openTheConnectionPool();
    List<HttpResponse> responses = burst(10, userId, roomTypeId, night, call -> "shared-key");
    long bookingId = read(responses.get(0)).get("id").asLong();
    for (HttpResponse response : responses) {
      assertEquals(201, response.status());
      assertEquals(bookingId, read(response).get("id").asLong());
    }
    assertEquals(1, Fixtures.countBookings(jdbc()));
    assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, night));
  }

  @Test
  void oneHundredHoldsOnFiveRoomsAcceptFiveAndRejectTheRest() throws Exception {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate night = LocalDate.of(2026, 12, 20);
    Fixtures.insertInventory(jdbc(), roomTypeId, night, ROOM_TOTAL, 0);
    openTheConnectionPool();
    int created = 0;
    int conflict = 0;
    int index = 0;
    for (HttpResponse response :
        burst(OVERBOOK_ATTEMPTS, userId, roomTypeId, night, call -> "book-" + call)) {
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

  @Test
  void holdPastHoldExpiresAtExpiresAndFreesTheRoom() throws Exception {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate night = LocalDate.of(2026, 12, 20);
    Fixtures.insertInventory(jdbc(), roomTypeId, night, 1, 1);
    long expiredHold =
        Fixtures.insertBooking(
            jdbc(),
            userId,
            roomTypeId,
            night,
            night.plusDays(1),
            "HELD",
            "expired-hold",
            Instant.now().minus(Duration.ofMinutes(1)));
    HttpResponse rebooked = bookUntilNotConflict(userId, roomTypeId, night, EXPIRY_WINDOW);
    assertEquals(CREATED, rebooked.status());
    assertEquals("EXPIRED", Fixtures.bookingStatus(jdbc(), expiredHold));
    assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, night));
  }

  private HttpResponse bookUntilNotConflict(
      long userId, long roomTypeId, LocalDate night, Duration within) throws InterruptedException {
    long deadline = System.nanoTime() + within.toNanos();
    for (int attempt = 0; ; attempt++) {
      HttpResponse response =
          postBooking(userId, roomTypeId, night, night.plusDays(1), "after-expiry-" + attempt);
      if (response.status() != CONFLICT || System.nanoTime() > deadline) {
        return response;
      }
      Thread.sleep(200);
    }
  }

  private List<HttpResponse> burst(
      int threads, long userId, long roomTypeId, LocalDate night, IntFunction<String> keyForCall)
      throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(threads);
    List<Future<HttpResponse>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        int call = i;
        futures.add(
            pool.submit(
                () -> {
                  barrier.await(30, TimeUnit.SECONDS);
                  return postBooking(
                      userId, roomTypeId, night, night.plusDays(1), keyForCall.apply(call));
                }));
      }
      List<HttpResponse> responses = new ArrayList<>();
      for (Future<HttpResponse> future : futures) {
        responses.add(future.get(30, TimeUnit.SECONDS));
      }
      return responses;
    }
  }
}

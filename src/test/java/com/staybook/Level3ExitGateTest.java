package com.staybook;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("level3")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class Level3ExitGateTest extends ExitGateSupport {

  private static final String WEBHOOK_SECRET = "test-webhook-secret";
  private static final int GATEWAY_DELAY_MILLIS = 5000;
  private static final String ORDERS = "/v1/orders";

  private static WireMockServer wireMock;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    SharedPostgres.register(registry);
    registry.add(
        "staybook.payment.gateway-base-url", Level3ExitGateTest::startGatewayAndReturnBaseUrl);
    registry.add("staybook.payment.webhook-secret", () -> WEBHOOK_SECRET);
  }

  @BeforeAll
  static void startGateway() {
    startGatewayAndReturnBaseUrl();
  }

  @AfterAll
  static void stopGateway() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @BeforeEach
  void resetGateway() {
    wireMock.resetRequests();
  }

  @Test
  void fiveIdenticalSignedWebhooksLeaveOnePaidPayment() throws Exception {
    long bookingId = insertBooking("CONFIRMED_UNPAID", "webhook-booking");
    String body = webhookBody(bookingId, "order_123", "pay-1");
    String signature = sign(body);
    CyclicBarrier barrier = new CyclicBarrier(5);
    try (ExecutorService pool = Executors.newFixedThreadPool(5)) {
      List<Future<Integer>> futures = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        futures.add(
            pool.submit(
                () -> {
                  barrier.await(30, TimeUnit.SECONDS);
                  return postWebhook(body, signature).status();
                }));
      }
      for (Future<Integer> future : futures) {
        assertEquals(200, future.get(30, TimeUnit.SECONDS));
      }
    }
    assertEquals(1, Fixtures.countPayments(jdbc(), "order_123"));
    assertEquals("PAID", Fixtures.paymentStatus(jdbc(), "order_123"));
    assertEquals("PAID", Fixtures.bookingStatus(jdbc(), bookingId));
  }

  @Test
  void badSignatureReturns401AndWritesNothing() throws Exception {
    long bookingId = insertBooking("CONFIRMED_UNPAID", "bad-signature-booking");
    String body = webhookBody(bookingId, "order_bad", "pay-bad");
    HttpResponse response = postWebhook(body, "00");
    assertEquals(401, response.status());
    assertEquals(0, Fixtures.countPayments(jdbc()));
    assertEquals("CONFIRMED_UNPAID", Fixtures.bookingStatus(jdbc(), bookingId));
  }

  @Test
  void bothHoldsCommitWithinOneSecondWhileTheGatewaySleeps() throws Exception {
    NightStay stay = insertNight(2);
    openTheConnectionPool();
    wireMock.resetRequests();
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<HttpResponse> first =
          pool.submit(
              () ->
                  postBooking(
                      stay.userId(),
                      stay.roomTypeId(),
                      stay.night(),
                      stay.night().plusDays(1),
                      "slow-1"));
      Future<HttpResponse> second =
          pool.submit(
              () ->
                  postBooking(
                      stay.userId(),
                      stay.roomTypeId(),
                      stay.night(),
                      stay.night().plusDays(1),
                      "slow-2"));
      assertTrue(reservedReaches(stay, 2, Duration.ofSeconds(1)));
      assertEquals(201, first.get(20, TimeUnit.SECONDS).status());
      assertEquals(201, second.get(20, TimeUnit.SECONDS).status());
    }
    assertEquals(2, Fixtures.reserved(jdbc(), stay.roomTypeId(), stay.night()));
    wireMock.verify(2, postRequestedFor(urlEqualTo(ORDERS)));
  }

  @Test
  void gatewayCallInsideTheTransactionLeavesReservedBelowTwoForOneSecond() throws Exception {
    NightStay stay = insertNight(2);
    openTheConnectionPool();
    wireMock.resetRequests();
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<HttpResponse> first =
          pool.submit(
              () ->
                  postBookingCallingGatewayInsideTheTransaction(
                      stay.userId(),
                      stay.roomTypeId(),
                      stay.night(),
                      stay.night().plusDays(1),
                      "ctrl-1"));
      Future<HttpResponse> second =
          pool.submit(
              () ->
                  postBooking(
                      stay.userId(),
                      stay.roomTypeId(),
                      stay.night(),
                      stay.night().plusDays(1),
                      "ctrl-2"));
      assertFalse(reservedReaches(stay, 2, Duration.ofSeconds(1)));
      assertEquals(201, first.get(20, TimeUnit.SECONDS).status());
      assertEquals(201, second.get(20, TimeUnit.SECONDS).status());
    }
    assertEquals(2, Fixtures.reserved(jdbc(), stay.roomTypeId(), stay.night()));
    wireMock.verify(2, postRequestedFor(urlEqualTo(ORDERS)));
  }

  private NightStay insertNight(int total) {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate night = LocalDate.of(2026, 12, 20);
    Fixtures.insertInventory(jdbc(), roomTypeId, night, total, 0);
    return new NightStay(userId, roomTypeId, night);
  }

  private long insertBooking(String status, String idempotencyKey) {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate night = LocalDate.of(2026, 12, 20);
    return Fixtures.insertBooking(
        jdbc(), userId, roomTypeId, night, night.plusDays(1), status, idempotencyKey);
  }

  private boolean reservedReaches(NightStay stay, int expected, Duration within)
      throws InterruptedException {
    long deadline = System.nanoTime() + within.toNanos();
    while (System.nanoTime() < deadline) {
      if (Fixtures.reserved(jdbc(), stay.roomTypeId(), stay.night()) >= expected) {
        return true;
      }
      Thread.sleep(25);
    }
    return Fixtures.reserved(jdbc(), stay.roomTypeId(), stay.night()) >= expected;
  }

  private static String webhookBody(long bookingId, String providerRef, String idempotencyKey) {
    return """
        {"providerRef":"%s","bookingId":%d,"amount":1500,\
        "paymentType":"card","idempotencyKey":"%s"}\
        """
        .formatted(providerRef, bookingId, idempotencyKey);
  }

  private static String sign(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(raw);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  // Spring reads gateway-base-url while the context starts, which can be before @BeforeAll.
  private static synchronized String startGatewayAndReturnBaseUrl() {
    if (wireMock == null) {
      wireMock = new WireMockServer(wireMockConfig().dynamicPort());
      wireMock.start();
      wireMock.stubFor(
          post(urlEqualTo(ORDERS))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"id\":\"order_test\",\"status\":\"created\"}")
                      .withFixedDelay(GATEWAY_DELAY_MILLIS)));
    }
    return wireMock.baseUrl();
  }

  private record NightStay(long userId, long roomTypeId, LocalDate night) {}
}

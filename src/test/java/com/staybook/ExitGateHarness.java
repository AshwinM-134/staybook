package com.staybook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.RequestBodySpec;

class ExitGateHarness {

  private final ObjectMapper mapper = new ObjectMapper();

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate database;

  @Autowired private Flyway migrations;

  private WebTestClient client;

  @BeforeEach
  void resetDatabase() {
    Fixtures.reset(database, migrations);
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  final JdbcTemplate jdbc() {
    return database;
  }

  final Flyway flyway() {
    return migrations;
  }

  final void warmup() {
    client.get().uri("/api/hotels/1").exchange().expectBody().returnResult();
  }

  final HttpExchange postJson(String uri, String json) {
    return exchange(client.post().uri(uri), json, false);
  }

  final HttpExchange postBooking(
      long userId,
      long roomTypeId,
      LocalDate checkIn,
      LocalDate checkOut,
      String idempotencyKey,
      boolean insideTransaction) {
    String json = bookingJson(userId, roomTypeId, checkIn, checkOut, idempotencyKey);
    return exchange(client.post().uri("/api/bookings"), json, insideTransaction);
  }

  final HttpExchange postWebhook(String json, String signature) {
    var result =
        client
            .post()
            .uri("/api/payments/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Payment-Signature", signature)
            .bodyValue(json)
            .exchange()
            .expectBody(byte[].class)
            .returnResult();
    return new HttpExchange(result.getStatus().value(), result.getResponseBody());
  }

  final void release(long bookingId) {
    client
        .post()
        .uri("/api/bookings/{id}/release", bookingId)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  final JsonNode read(HttpExchange exchange) {
    try {
      return mapper.readTree(exchange.body());
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  final HttpExchange getJson(String uri) {
    var result = client.get().uri(uri).exchange().expectBody(byte[].class).returnResult();
    return new HttpExchange(result.getStatus().value(), result.getResponseBody());
  }

  final void getNotFound(String uri) {
    client.get().uri(uri).exchange().expectStatus().isNotFound();
  }

  final HttpExchange putJson(String uri, String json) {
    return exchange(client.put().uri(uri), json, false);
  }

  final void deleteNoContent(String uri) {
    client.delete().uri(uri).exchange().expectStatus().isNoContent();
  }

  private HttpExchange exchange(RequestBodySpec spec, String json, boolean insideTransaction) {
    if (insideTransaction) {
      spec.header("X-Staybook-Call-Gateway-Inside-Transaction", "true");
    }
    var result =
        spec.contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody(byte[].class)
            .returnResult();
    return new HttpExchange(result.getStatus().value(), result.getResponseBody());
  }

  private static String bookingJson(
      long userId, long roomTypeId, LocalDate checkIn, LocalDate checkOut, String idempotencyKey) {
    return "{\"userId\":"
        + userId
        + ",\"roomTypeId\":"
        + roomTypeId
        + ",\"checkIn\":\""
        + checkIn
        + "\",\"checkOut\":\""
        + checkOut
        + "\",\"guests\":2,\"idempotencyKey\":\""
        + idempotencyKey
        + "\"}";
  }
}

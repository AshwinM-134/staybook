package com.staybook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.RequestBodySpec;

class ExitGateSupport {

  private final ObjectMapper mapper = new ObjectMapper();

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate database;

  @Autowired private Flyway migrations;

  private WebTestClient client;

  @BeforeEach
  void resetDatabase() {
    Fixtures.reset(database, migrations);
    client =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(30))
            .build();
  }

  final JdbcTemplate jdbc() {
    return database;
  }

  final Flyway flyway() {
    return migrations;
  }

  final void openTheConnectionPool() {
    client.get().uri("/api/hotels/1").exchange().expectBody().returnResult();
  }

  final HttpResponse postJson(String uri, String json) {
    return exchange(client.post().uri(uri), json);
  }

  final HttpResponse postBooking(
      long userId, long roomTypeId, LocalDate checkIn, LocalDate checkOut, String idempotencyKey) {
    return exchange(
        client.post().uri("/api/bookings"),
        bookingJson(userId, roomTypeId, checkIn, checkOut, idempotencyKey));
  }

  final HttpResponse postWebhook(String json, String signature) {
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
    return new HttpResponse(result.getStatus().value(), result.getResponseBody());
  }

  final void release(long bookingId) {
    client
        .post()
        .uri("/api/bookings/{id}/release", bookingId)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  final JsonNode read(HttpResponse response) {
    try {
      return mapper.readTree(response.body());
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  final HttpResponse getJson(String uri) {
    var result = client.get().uri(uri).exchange().expectBody(byte[].class).returnResult();
    return new HttpResponse(result.getStatus().value(), result.getResponseBody());
  }

  final void getNotFound(String uri) {
    client.get().uri(uri).exchange().expectStatus().isNotFound();
  }

  final HttpResponse putJson(String uri, String json) {
    return exchange(client.put().uri(uri), json);
  }

  final void deleteNoContent(String uri) {
    client.delete().uri(uri).exchange().expectStatus().isNoContent();
  }

  private HttpResponse exchange(RequestBodySpec spec, String json) {
    var result =
        spec.contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectBody(byte[].class)
            .returnResult();
    return new HttpResponse(result.getStatus().value(), result.getResponseBody());
  }

  private static String bookingJson(
      long userId, long roomTypeId, LocalDate checkIn, LocalDate checkOut, String idempotencyKey) {
    return """
        {"userId":%d,"roomTypeId":%d,"checkIn":"%s",\
        "checkOut":"%s","guests":2,"idempotencyKey":"%s"}\
        """
        .formatted(userId, roomTypeId, checkIn, checkOut, idempotencyKey);
  }
}

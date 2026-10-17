package com.staybook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("level0")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class Level0ExitGateTest extends ExitGateHarness {

  private static final List<String> ENTITY_TABLES =
      List.of("users", "hotel", "room_type", "inventory", "rate", "booking", "payment");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresSupport.register(registry);
  }

  @Test
  void flywayReapplyIsSafeAndEntityTablesExist() {
    flyway().migrate();
    for (String table : ENTITY_TABLES) {
      Integer found =
          jdbc()
              .queryForObject(
                  "select count(*) from information_schema.tables"
                      + " where table_schema = 'public' and table_name = ?",
                  Integer.class,
                  table);
      assertEquals(1, found, table);
    }
  }

  @Test
  void reservedGreaterThanTotalIsRejected() {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate night = LocalDate.of(2026, 12, 20);
    DataIntegrityViolationException failure =
        assertThrows(
            DataIntegrityViolationException.class,
            () -> Fixtures.insertInventory(jdbc(), roomTypeId, night, 1, 2));
    assertEquals("23514", sqlState(failure));
  }

  @Test
  void holdUpdatesReservedOnEachNight() {
    long userId = Fixtures.insertUser(jdbc());
    long roomTypeId = seedRoomFor(userId, 5, 0);
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    LocalDate checkOut = checkIn.plusDays(3);
    HttpExchange exchange = postBooking(userId, roomTypeId, checkIn, checkOut, "hold-3", false);
    assertEquals(201, exchange.status());
    JsonNode body = read(exchange);
    assertEquals("HELD", body.get("status").asText());
    for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
      assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, night), night.toString());
    }
  }

  @Test
  void fullNightRejectsHoldAndChangesNothing() {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    Fixtures.insertInventory(jdbc(), roomTypeId, checkIn, 5, 0);
    Fixtures.insertInventory(jdbc(), roomTypeId, checkIn.plusDays(1), 5, 0);
    Fixtures.insertInventory(jdbc(), roomTypeId, checkIn.plusDays(2), 1, 1);
    HttpExchange exchange =
        postBooking(userId, roomTypeId, checkIn, checkIn.plusDays(3), "hold-full", false);
    assertEquals(409, exchange.status());
    assertEquals(0, Fixtures.reserved(jdbc(), roomTypeId, checkIn));
    assertEquals(0, Fixtures.reserved(jdbc(), roomTypeId, checkIn.plusDays(1)));
    assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, checkIn.plusDays(2)));
    assertEquals(0, Fixtures.countBookings(jdbc()));
  }

  @Test
  void releaseDecrementsReservedOnEachNight() {
    long userId = Fixtures.insertUser(jdbc());
    long roomTypeId = seedRoomFor(userId, 5, 0);
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    LocalDate checkOut = checkIn.plusDays(3);
    HttpExchange created =
        postBooking(userId, roomTypeId, checkIn, checkOut, "hold-release", false);
    assertEquals(201, created.status());
    long bookingId = read(created).get("id").asLong();
    release(bookingId);
    for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
      assertEquals(0, Fixtures.reserved(jdbc(), roomTypeId, night), night.toString());
    }
  }

  @Test
  void sameIdempotencyKeyReturnsSameBooking() {
    long userId = Fixtures.insertUser(jdbc());
    long roomTypeId = seedRoomFor(userId, 5, 0);
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    LocalDate checkOut = checkIn.plusDays(1);
    HttpExchange first = postBooking(userId, roomTypeId, checkIn, checkOut, "same-key", false);
    HttpExchange second = postBooking(userId, roomTypeId, checkIn, checkOut, "same-key", false);
    assertEquals(201, first.status());
    assertEquals(201, second.status());
    assertEquals(read(first).get("id").asLong(), read(second).get("id").asLong());
    assertEquals(1, Fixtures.countBookings(jdbc()));
    assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, checkIn));
  }

  @Test
  void hotelAndRoomTypeCrudAndMissingIsNotFound() {
    getNotFound("/api/hotels/999999");
    getNotFound("/api/room-types/999999");
    long ownerId = Fixtures.insertUser(jdbc());
    String hotelJson =
        "{\"ownerId\":"
            + ownerId
            + ",\"name\":\"Harbor\",\"city\":\"Kochi\",\"address\":\"1 Beach Road\"}";
    HttpExchange createdHotel = postJson("/api/hotels", hotelJson);
    assertEquals(201, createdHotel.status());
    long hotelId = read(createdHotel).get("id").asLong();
    JsonNode hotel = read(getJson("/api/hotels/" + hotelId));
    assertEquals("Harbor", hotel.get("name").asText());
    assertEquals(ownerId, hotel.get("ownerId").asLong());
    String renamed =
        "{\"ownerId\":"
            + ownerId
            + ",\"name\":\"Harbor Inn\",\"city\":\"Kochi\",\"address\":\"1 Beach Road\"}";
    HttpExchange updated = putJson("/api/hotels/" + hotelId, renamed);
    assertEquals(200, updated.status());
    assertEquals("Harbor Inn", read(getJson("/api/hotels/" + hotelId)).get("name").asText());

    String roomJson = "{\"hotelId\":" + hotelId + ",\"name\":\"Deluxe\",\"capacity\":2}";
    HttpExchange createdRoom = postJson("/api/room-types", roomJson);
    assertEquals(201, createdRoom.status());
    long roomTypeId = read(createdRoom).get("id").asLong();
    JsonNode room = read(getJson("/api/room-types/" + roomTypeId));
    assertEquals(hotelId, room.get("hotelId").asLong());
    assertEquals("Deluxe", room.get("name").asText());
    assertEquals(2, room.get("capacity").asInt());
    String renamedRoom = "{\"hotelId\":" + hotelId + ",\"name\":\"Suite\",\"capacity\":3}";
    assertEquals(200, putJson("/api/room-types/" + roomTypeId, renamedRoom).status());
    assertEquals("Suite", read(getJson("/api/room-types/" + roomTypeId)).get("name").asText());

    deleteNoContent("/api/room-types/" + roomTypeId);
    getNotFound("/api/room-types/" + roomTypeId);
    deleteNoContent("/api/hotels/" + hotelId);
    getNotFound("/api/hotels/" + hotelId);
  }

  private long seedRoomFor(long userId, int total, int reserved) {
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    for (int offset = 0; offset < 3; offset++) {
      Fixtures.insertInventory(jdbc(), roomTypeId, checkIn.plusDays(offset), total, reserved);
    }
    return roomTypeId;
  }

  private static String sqlState(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof SQLException sqlException) {
        return sqlException.getSQLState();
      }
      current = current.getCause();
    }
    return "";
  }
}

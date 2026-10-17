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
class Level0ExitGateTest extends ExitGateSupport {

  private static final List<String> ENTITY_TABLES =
      List.of("users", "hotel", "room_type", "inventory", "rate", "booking", "payment");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    SharedPostgres.register(registry);
  }

  @Test
  void flywayMigrateAgainStillHasTheEntityTables() {
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
  void reservedAboveTotalIsRejected() {
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
  void negativeReservedIsRejected() {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate night = LocalDate.of(2026, 12, 20);
    DataIntegrityViolationException failure =
        assertThrows(
            DataIntegrityViolationException.class,
            () -> Fixtures.insertInventory(jdbc(), roomTypeId, night, 1, -1));
    assertEquals("23514", sqlState(failure));
  }

  @Test
  void holdIncrementsReservedOnEachNight() {
    long userId = Fixtures.insertUser(jdbc());
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    int nights = 3;
    LocalDate checkOut = checkIn.plusDays(nights);
    long roomTypeId = insertNights(userId, checkIn, nights, 5, 0);
    HttpResponse response = postBooking(userId, roomTypeId, checkIn, checkOut, "hold-3");
    assertEquals(201, response.status());
    JsonNode body = read(response);
    assertEquals("HELD", body.get("status").asText());
    for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
      assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, night), night.toString());
    }
  }

  @Test
  void fullNightReturns409AndLeavesReservedUnchanged() {
    long userId = Fixtures.insertUser(jdbc());
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    Fixtures.insertInventory(jdbc(), roomTypeId, checkIn, 5, 0);
    Fixtures.insertInventory(jdbc(), roomTypeId, checkIn.plusDays(1), 5, 0);
    Fixtures.insertInventory(jdbc(), roomTypeId, checkIn.plusDays(2), 1, 1);
    HttpResponse response =
        postBooking(userId, roomTypeId, checkIn, checkIn.plusDays(3), "hold-full");
    assertEquals(409, response.status());
    assertEquals(0, Fixtures.reserved(jdbc(), roomTypeId, checkIn));
    assertEquals(0, Fixtures.reserved(jdbc(), roomTypeId, checkIn.plusDays(1)));
    assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, checkIn.plusDays(2)));
    assertEquals(0, Fixtures.countBookings(jdbc()));
  }

  @Test
  void releaseSubtractsReservedOnEachNight() {
    long userId = Fixtures.insertUser(jdbc());
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    int nights = 3;
    LocalDate checkOut = checkIn.plusDays(nights);
    long roomTypeId = insertNights(userId, checkIn, nights, 5, 0);
    HttpResponse created = postBooking(userId, roomTypeId, checkIn, checkOut, "hold-release");
    assertEquals(201, created.status());
    long bookingId = read(created).get("id").asLong();
    release(bookingId);
    for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
      assertEquals(0, Fixtures.reserved(jdbc(), roomTypeId, night), night.toString());
    }
  }

  @Test
  void releasingTwiceLeavesReservedAtZero() {
    long userId = Fixtures.insertUser(jdbc());
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    int nights = 2;
    LocalDate checkOut = checkIn.plusDays(nights);
    long roomTypeId = insertNights(userId, checkIn, nights, 5, 0);
    HttpResponse created = postBooking(userId, roomTypeId, checkIn, checkOut, "release-twice");
    assertEquals(201, created.status());
    long bookingId = read(created).get("id").asLong();
    release(bookingId);
    release(bookingId);
    for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
      assertEquals(0, Fixtures.reserved(jdbc(), roomTypeId, night), night.toString());
    }
  }

  @Test
  void theSameIdempotencyKeyWithAnotherBodyReturns422() {
    long userId = Fixtures.insertUser(jdbc());
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    long roomTypeId = insertNights(userId, checkIn, 2, 5, 0);
    HttpResponse first =
        postBooking(userId, roomTypeId, checkIn, checkIn.plusDays(1), "reused-key");
    HttpResponse second =
        postBooking(userId, roomTypeId, checkIn, checkIn.plusDays(2), "reused-key");
    assertEquals(201, first.status());
    assertEquals(422, second.status());
    assertEquals(1, Fixtures.countBookings(jdbc()));
    assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, checkIn));
    assertEquals(0, Fixtures.reserved(jdbc(), roomTypeId, checkIn.plusDays(1)));
  }

  @Test
  void theSameIdempotencyKeyReservesOnceAndReturnsTheSameBooking() {
    long userId = Fixtures.insertUser(jdbc());
    LocalDate checkIn = LocalDate.of(2026, 12, 20);
    int nights = 1;
    LocalDate checkOut = checkIn.plusDays(nights);
    long roomTypeId = insertNights(userId, checkIn, nights, 5, 0);
    HttpResponse first = postBooking(userId, roomTypeId, checkIn, checkOut, "same-key");
    HttpResponse second = postBooking(userId, roomTypeId, checkIn, checkOut, "same-key");
    assertEquals(201, first.status());
    assertEquals(201, second.status());
    assertEquals(read(first).get("id").asLong(), read(second).get("id").asLong());
    assertEquals(1, Fixtures.countBookings(jdbc()));
    assertEquals(1, Fixtures.reserved(jdbc(), roomTypeId, checkIn));
  }

  @Test
  void missingHotelOrRoomTypeIsNotFoundAndBothCanBeCreatedUpdatedAndDeleted() {
    getNotFound("/api/hotels/999999");
    getNotFound("/api/room-types/999999");
    long ownerId = Fixtures.insertUser(jdbc());
    String hotelJson =
        """
        {"ownerId":%d,"name":"Harbor","city":"Kochi","address":"1 Beach Road"}\
        """
            .formatted(ownerId);
    HttpResponse createdHotel = postJson("/api/hotels", hotelJson);
    assertEquals(201, createdHotel.status());
    long hotelId = read(createdHotel).get("id").asLong();
    JsonNode hotel = read(getJson("/api/hotels/" + hotelId));
    assertEquals("Harbor", hotel.get("name").asText());
    assertEquals(ownerId, hotel.get("ownerId").asLong());
    String renamed =
        """
        {"ownerId":%d,"name":"Harbor Inn","city":"Kochi","address":"1 Beach Road"}\
        """
            .formatted(ownerId);
    HttpResponse updated = putJson("/api/hotels/" + hotelId, renamed);
    assertEquals(200, updated.status());
    assertEquals("Harbor Inn", read(getJson("/api/hotels/" + hotelId)).get("name").asText());

    String roomJson =
        """
        {"hotelId":%d,"name":"Deluxe","capacity":2}\
        """
            .formatted(hotelId);
    HttpResponse createdRoom = postJson("/api/room-types", roomJson);
    assertEquals(201, createdRoom.status());
    long roomTypeId = read(createdRoom).get("id").asLong();
    JsonNode room = read(getJson("/api/room-types/" + roomTypeId));
    assertEquals(hotelId, room.get("hotelId").asLong());
    assertEquals("Deluxe", room.get("name").asText());
    assertEquals(2, room.get("capacity").asInt());
    String renamedRoom =
        """
        {"hotelId":%d,"name":"Suite","capacity":3}\
        """
            .formatted(hotelId);
    assertEquals(200, putJson("/api/room-types/" + roomTypeId, renamedRoom).status());
    assertEquals("Suite", read(getJson("/api/room-types/" + roomTypeId)).get("name").asText());

    deleteNoContent("/api/room-types/" + roomTypeId);
    getNotFound("/api/room-types/" + roomTypeId);
    deleteNoContent("/api/hotels/" + hotelId);
    getNotFound("/api/hotels/" + hotelId);
  }

  private long insertNights(
      long userId, LocalDate firstNight, int nights, int total, int reserved) {
    long hotelId = Fixtures.insertHotel(jdbc(), userId);
    long roomTypeId = Fixtures.insertRoomType(jdbc(), hotelId);
    for (int offset = 0; offset < nights; offset++) {
      Fixtures.insertInventory(jdbc(), roomTypeId, firstNight.plusDays(offset), total, reserved);
    }
    return roomTypeId;
  }

  // SQLSTATE sits on the nested Postgres SQLException, not on Spring's wrapper.
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

package com.staybook;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts and truncates rows. Schema lives in V1__init.sql. A missing table fails the test. */
final class Fixtures {

  static final long TOTAL_PRICE_PAISE = 150_000L;

  private Fixtures() {}

  static void reset(JdbcTemplate jdbc, Flyway flyway) {
    String tables =
        jdbc.queryForObject(
            "select string_agg(quote_ident(tablename), ', ') from pg_tables"
                + " where schemaname = 'public' and tablename <> ?",
            String.class,
            flyway.getConfiguration().getTable());
    if (tables != null) {
      jdbc.execute("truncate table " + tables + " restart identity cascade");
    }
  }

  static long insertUser(JdbcTemplate jdbc) {
    Long id =
        jdbc.queryForObject(
            "insert into users (username, email, password_hash, created_at)"
                + " values (?, ?, ?, now()) returning id",
            Long.class,
            "guest",
            "guest@example.com",
            "hash");
    return id == null ? 0L : id;
  }

  static long insertHotel(JdbcTemplate jdbc, long ownerId) {
    Long id =
        jdbc.queryForObject(
            "insert into hotel (owner_id, name, city, address) values (?, ?, ?, ?) returning id",
            Long.class,
            ownerId,
            "Harbor",
            "Kochi",
            "1 Beach Road");
    return id == null ? 0L : id;
  }

  static long insertRoomType(JdbcTemplate jdbc, long hotelId) {
    Long id =
        jdbc.queryForObject(
            "insert into room_type (hotel_id, name, capacity) values (?, ?, ?) returning id",
            Long.class,
            hotelId,
            "Deluxe",
            2);
    return id == null ? 0L : id;
  }

  static void insertInventory(
      JdbcTemplate jdbc, long roomTypeId, LocalDate date, int total, int reserved) {
    jdbc.update(
        "insert into inventory (room_type_id, date, total, reserved) values (?, ?, ?, ?)",
        roomTypeId,
        Date.valueOf(date),
        total,
        reserved);
  }

  static int reserved(JdbcTemplate jdbc, long roomTypeId, LocalDate date) {
    Integer value =
        jdbc.queryForObject(
            "select reserved from inventory where room_type_id = ? and date = ?",
            Integer.class,
            roomTypeId,
            Date.valueOf(date));
    return value == null ? 0 : value;
  }

  static long insertBooking(
      JdbcTemplate jdbc,
      long userId,
      long roomTypeId,
      LocalDate checkIn,
      LocalDate checkOut,
      String status,
      String idempotencyKey,
      Instant holdExpiresAt) {
    Long id =
        jdbc.queryForObject(
            "insert into booking (user_id, room_type_id, check_in, check_out, guests,"
                + " total_price_paise, status, idempotency_key, request_hash, hold_expires_at,"
                + " created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()) returning id",
            Long.class,
            userId,
            roomTypeId,
            Date.valueOf(checkIn),
            Date.valueOf(checkOut),
            2,
            TOTAL_PRICE_PAISE,
            status,
            idempotencyKey,
            "fixture-" + idempotencyKey,
            Timestamp.from(holdExpiresAt));
    return id == null ? 0L : id;
  }

  static int countBookings(JdbcTemplate jdbc) {
    Integer count = jdbc.queryForObject("select count(*) from booking", Integer.class);
    return count == null ? 0 : count;
  }

  static String bookingStatus(JdbcTemplate jdbc, long bookingId) {
    return jdbc.queryForObject("select status from booking where id = ?", String.class, bookingId);
  }

  static int countPayments(JdbcTemplate jdbc) {
    Integer count = jdbc.queryForObject("select count(*) from payment", Integer.class);
    return count == null ? 0 : count;
  }

  static int countPayments(JdbcTemplate jdbc, String providerRef) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from payment where provider_ref = ?", Integer.class, providerRef);
    return count == null ? 0 : count;
  }

  static String paymentStatus(JdbcTemplate jdbc, String providerRef) {
    return jdbc.queryForObject(
        "select status from payment where provider_ref = ?", String.class, providerRef);
  }
}

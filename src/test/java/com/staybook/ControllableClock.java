package com.staybook;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock fixed at a known instant that tests can move forward. */
final class ControllableClock extends Clock {

  static final Instant START = Instant.parse("2027-01-01T00:00:00Z");

  private static final ZoneId ZONE = ZoneOffset.UTC;

  private volatile Instant current;

  ControllableClock(Instant start) {
    current = start;
  }

  void advance(Duration by) {
    current = current.plus(by);
  }

  void reset() {
    current = START;
  }

  @Override
  public ZoneId getZone() {
    return ZONE;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    if (ZONE.equals(zone)) {
      return this;
    }
    return Clock.fixed(current, zone);
  }

  @Override
  public Instant instant() {
    return current;
  }
}

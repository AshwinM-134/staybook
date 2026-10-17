package com.staybook;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** A clock tests can move forward. A withZone copy reads the same instant. */
final class ControllableClock extends Clock {

  static final Instant START = Instant.parse("2099-01-01T00:00:00Z");

  private final AtomicReference<Instant> current;

  private final ZoneId zone;

  ControllableClock(Instant start) {
    this(new AtomicReference<>(Objects.requireNonNull(start, "start")), ZoneOffset.UTC);
  }

  private ControllableClock(AtomicReference<Instant> current, ZoneId zone) {
    this.current = current;
    this.zone = zone;
  }

  void advance(Duration by) {
    current.updateAndGet(instant -> instant.plus(by));
  }

  void reset() {
    current.set(START);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zoneId) {
    Objects.requireNonNull(zoneId, "zone");
    if (zoneId.equals(zone)) {
      return this;
    }
    return new ControllableClock(current, zoneId);
  }

  @Override
  public Instant instant() {
    return current.get();
  }
}

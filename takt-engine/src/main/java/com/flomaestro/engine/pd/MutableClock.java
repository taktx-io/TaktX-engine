package com.flomaestro.engine.pd;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public class MutableClock extends Clock {

  private final ZoneId zone;
  private Instant instant;

  public MutableClock(Instant instant, ZoneId zone) {
    this.instant = instant;
    this.zone = zone;
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    if (zone.equals(this.zone)) {
      return this;
    }
    return new MutableClock(instant, zone);
  }

  @Override
  public long millis() {
    return instant.toEpochMilli();
  }

  @Override
  public Instant instant() {
    return instant;
  }

  public void advanceBy(Duration duration) {
    instant = instant.plus(duration);
  }

  public void set(Instant newInstant) {
    instant = newInstant;
  }
}

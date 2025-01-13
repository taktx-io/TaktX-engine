package com.flomaestro.engine.generic;

import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Alternative
public class FixedClockProducer {

  public static final String INITIAL_TIME = "2024-02-28T10:00:00Z";
  public static final MutableClock FIXED_CLOCK =
      new MutableClock(Instant.parse(INITIAL_TIME), ZoneId.of("UTC"));

  @Produces
  Clock produceFixedClock() {
    return FIXED_CLOCK;
  }
}

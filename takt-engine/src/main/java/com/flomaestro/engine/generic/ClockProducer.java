package com.flomaestro.engine.generic;

import jakarta.enterprise.inject.Produces;
import java.time.Clock;

public class ClockProducer {
  @Produces
  Clock produceClock() {
    return Clock.systemUTC();
  }
}

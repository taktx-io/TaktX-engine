package com.flomaestro.engine.pd;

import jakarta.enterprise.inject.Produces;
import java.time.Clock;

public class ClockProducer {
  @Produces
  Clock produceClock() {
    return Clock.systemUTC();
  }
}

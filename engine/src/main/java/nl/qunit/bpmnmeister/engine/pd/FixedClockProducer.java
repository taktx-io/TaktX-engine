package nl.qunit.bpmnmeister.engine.pd;

import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Alternative
public class FixedClockProducer {
  @Produces
  Clock produceFixedClock() {
    return new MutableClock(Instant.parse("2024-02-28T10:00:00Z"), ZoneId.of("UTC"));
  }
}

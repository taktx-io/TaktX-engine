/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.generic;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
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

/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.generic;

import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Alternative
public class FixedClockProducer {

  public static final String INITIAL_TIME = "2024-02-28T10:00:00Z";
  public static final MutableClock FIXED_CLOCK = new MutableClock(Instant.parse(INITIAL_TIME), ZoneId.of("UTC"));

  @Produces
  public Clock produceClock() {
    return FIXED_CLOCK;
  }
}

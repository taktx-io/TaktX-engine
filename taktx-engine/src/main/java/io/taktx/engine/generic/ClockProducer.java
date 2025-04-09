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

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class ClockProducer {

  public static final String INITIAL_TIME = "2024-02-28T10:00:00Z";
  public static final Clock FIXED_CLOCK =
      new MutableClock(Instant.parse(INITIAL_TIME), ZoneId.of("UTC"));
  public static final Clock SYSTEM_CLOCK = Clock.systemUTC();

  @Inject
  @ConfigProperty(name = "quarkus.profile")
  String activeProfile;

  @Produces
  Clock produceClock() {
    if ("test".equals(activeProfile)) {
      // Return a fixed clock for testing
      return FIXED_CLOCK;
    } else {
      // Return a system clock for other profiles
      return SYSTEM_CLOCK;
    }
  }
}

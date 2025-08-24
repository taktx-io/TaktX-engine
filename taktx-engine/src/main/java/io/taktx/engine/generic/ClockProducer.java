/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.generic;

import io.taktx.engine.config.TaktConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ClockProducer {

  public static final String INITIAL_TIME = "2024-02-28T10:00:00Z";
  public static final Clock FIXED_CLOCK =
      new MutableClock(Instant.parse(INITIAL_TIME), ZoneId.of("UTC"));
  public static final Clock SYSTEM_CLOCK = Clock.systemUTC();

  final TaktConfiguration taktConfiguration;

  @Produces
  Clock produceClock() {
    if (taktConfiguration.inTestMode()) {
      // Return a fixed clock for testing
      log.info("Using fixed clock for testing");
      return FIXED_CLOCK;
    } else {
      // Return a system clock for other profiles
      return SYSTEM_CLOCK;
    }
  }
}

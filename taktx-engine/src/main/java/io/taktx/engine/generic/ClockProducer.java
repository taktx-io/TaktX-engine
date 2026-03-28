/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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

/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.SecurityEventDTO;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Bounded in-memory history of recent namespace security events. */
final class ClientSecurityEventStore {

  private final int maxEvents;
  private final Deque<SecurityEventDTO> recentEvents = new ArrayDeque<>();

  ClientSecurityEventStore(int maxEvents) {
    if (maxEvents <= 0) {
      throw new IllegalArgumentException("maxEvents must be > 0");
    }
    this.maxEvents = maxEvents;
  }

  synchronized void append(SecurityEventDTO event) {
    recentEvents.addLast(Objects.requireNonNull(event, "event"));
    while (recentEvents.size() > maxEvents) {
      recentEvents.removeFirst();
    }
  }

  synchronized List<SecurityEventDTO> snapshot() {
    return List.copyOf(recentEvents);
  }

  synchronized void clear() {
    recentEvents.clear();
  }
}

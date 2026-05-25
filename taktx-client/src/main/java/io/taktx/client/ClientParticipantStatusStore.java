/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.security.ParticipantStatusSupport;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory view of the latest participant-status records observed from the public status topic. */
final class ClientParticipantStatusStore {

  private final ConcurrentHashMap<String, ParticipantStatusDTO> statuses = new ConcurrentHashMap<>();

  void update(String recordKey, ParticipantStatusDTO status) {
    if (recordKey == null || recordKey.isBlank()) {
      throw new IllegalArgumentException("recordKey must not be blank");
    }
    statuses.put(recordKey, ParticipantStatusSupport.requireValid(status));
  }

  void remove(String recordKey) {
    if (recordKey == null || recordKey.isBlank()) {
      return;
    }
    statuses.remove(recordKey);
  }

  Map<String, ParticipantStatusDTO> snapshot() {
    return immutableSortedSnapshot(statuses);
  }

  Map<String, ParticipantStatusDTO> currentSnapshot(long nowMs) {
    LinkedHashMap<String, ParticipantStatusDTO> current = new LinkedHashMap<>();
    statuses.entrySet().stream()
        .filter(entry -> !ParticipantStatusSupport.isExpired(entry.getValue(), nowMs))
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> current.put(entry.getKey(), entry.getValue()));
    return Collections.unmodifiableMap(current);
  }

  private static Map<String, ParticipantStatusDTO> immutableSortedSnapshot(
      Map<String, ParticipantStatusDTO> source) {
    LinkedHashMap<String, ParticipantStatusDTO> snapshot = new LinkedHashMap<>();
    source.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
    return Collections.unmodifiableMap(snapshot);
  }
}


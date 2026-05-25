/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.config;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.security.ParticipantStatusSupport;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDI bean that holds the latest namespace participant status records received from the {@code
 * taktx-participant-status} compacted topic.
 */
@ApplicationScoped
public class ParticipantStatusStore {

  private final ConcurrentHashMap<String, ParticipantStatusDTO> statuses =
      new ConcurrentHashMap<>();

  public void update(String recordKey, ParticipantStatusDTO status) {
    statuses.put(recordKey, ParticipantStatusSupport.requireValid(status));
  }

  public void remove(String recordKey) {
    statuses.remove(recordKey);
  }

  public ParticipantStatusDTO get(String recordKey) {
    return statuses.get(recordKey);
  }

  public Map<String, ParticipantStatusDTO> snapshot() {
    return Map.copyOf(statuses);
  }

  public Map<String, ParticipantStatusDTO> currentSnapshot(long nowMs) {
    return statuses.entrySet().stream()
        .filter(entry -> !ParticipantStatusSupport.isExpired(entry.getValue(), nowMs))
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Map<String, ParticipantStatusDTO> currentSnapshot(
      Set<ParticipantCapability> requiredCapabilities, long nowMs) {
    return statuses.entrySet().stream()
        .filter(entry -> !ParticipantStatusSupport.isExpired(entry.getValue(), nowMs))
        .filter(
            entry ->
                requiredCapabilities == null
                    || requiredCapabilities.isEmpty()
                    || entry.getValue().getCapabilities().stream().anyMatch(requiredCapabilities::contains))
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}

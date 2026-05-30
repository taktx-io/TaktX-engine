/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ParticipantKind;
import io.taktx.dto.SecurityEventSeverity;
import io.taktx.dto.SecurityEventType;
import jakarta.annotation.Nullable;
import java.util.Map;

/** Simplified operator-facing blocker assembled from posture status and security-event signals. */
public record BlockingIssue(
    BlockingIssueSource source,
    String code,
    String message,
    @Nullable String participantInstanceId,
    @Nullable String participantId,
    @Nullable ParticipantKind participantKind,
    @Nullable String componentType,
    @Nullable SecurityEventType eventType,
    @Nullable SecurityEventSeverity severity,
    Map<String, String> metadata) {

  public BlockingIssue {
    metadata = metadata == null || metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
  }
}


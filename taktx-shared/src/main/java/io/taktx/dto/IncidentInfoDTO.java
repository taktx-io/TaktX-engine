/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IncidentInfoDTO {
  private List<Long> elementInstanceIdPath;
  private String message;
  private String[] stacktrace;

  /**
   * DLQ entry reference in the format {@code sourceTopic:partition:offset:sha256:hash} (or {@code
   * ?} for unknown hash). Only populated when the incident was caused by a message ingestion
   * failure that also produced a DLQ entry. Null when no corresponding DLQ entry exists. Use this
   * to navigate directly from an incident to the matching DLQ entry in the console.
   */
  private String dlqEntryRef;
}

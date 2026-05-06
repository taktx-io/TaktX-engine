/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RegisterForReflection
public class DlqReplayCommand {
  private String dlqEntryRef;
  private String operatorId;
  private long approvedAtMs;
  @Nullable private String operatorNotes;
  private byte[] correctedValueBytes;
  @Nullable private byte[] correctedKeyBytes;
  private Map<String, String> correctedHeaders;
  private String destinationTopic;
  private ReplayValidationPolicy validationPolicy;
  private DlqLineageDTO lineage;

  @Nullable private String overrideReason;
  @Nullable private List<String> changedFields;

  /** When {@code true} the processor validates all checks but does NOT forward the record. */
  private boolean dryRun;

  /**
   * Optional schema version of the corrected payload. If provided and it does not match the
   * engine's {@code SUPPORTED_SCHEMA_VERSION}, behaviour is governed by {@link
   * ReplayValidationPolicy}: {@code STRICT} rejects, {@code OPERATOR_OVERRIDE} warns.
   */
  @Nullable private Integer expectedSchemaVersion;
}

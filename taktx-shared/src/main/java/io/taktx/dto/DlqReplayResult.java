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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RegisterForReflection
public class DlqReplayResult {
  private String dlqEntryRef;
  private String operatorId;
  private long replayAtMs;
  private String status;
  private String outcomeText;

  @Nullable private DlqReasonCode failureReasonCode;
  @Nullable private String replaySigner;
  @Nullable private String replaySignatureKeyId;
  @Nullable private String compatibilityDecision;

  /** Mirrors the {@code dryRun} flag from the originating {@link DlqReplayCommand}. */
  private boolean dryRun;

  /** Reference linking this result back to the source DLQ entry ({@code dlqEntryRef}). */
  @Nullable private String lineageRef;
}

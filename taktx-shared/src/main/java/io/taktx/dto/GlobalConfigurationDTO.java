/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.*;

/**
 * Cluster-wide engine configuration distributed via the compacted {@code taktx-configuration}
 * topic.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class GlobalConfigurationDTO {
  @Builder.Default private boolean signingEnabled = false;
  @Builder.Default private boolean engineRequiresAuthorization = false;

  /** All key IDs accepted for signature verification. */
  @Builder.Default private List<String> trustedKeyIds = List.of();

  /** Cluster-wide DMN validation strictness. Defaults to preserving current behaviour. */
  @Builder.Default private DmnValidationMode dmnValidationMode = DmnValidationMode.PERMISSIVE;

  /**
   * Cluster-wide replay enforcement mode for protected command paths.
   *
   * <p>Defaults to {@link ReplayProtectionMode#COMPAT} to preserve the current gradual-rollout
   * posture: replay is enforced when a non-blank {@code auditId} is present, but blank values are
   * not yet rejected fail-closed.
   */
  @Builder.Default private ReplayProtectionMode replayProtectionMode = ReplayProtectionMode.COMPAT;

  /**
   * Retention window for replay identities in milliseconds.
   *
   * <p>Defaults to 10 minutes, matching the current in-memory nonce cache baseline until durable
   * replay storage replaces it.
   */
  @Builder.Default private long replayProtectionRetentionMs = 600_000L;
}

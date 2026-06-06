/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Latest-state oriented participant posture / readiness status for namespace security policy. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public class ParticipantStatusDTO {
  private String participantId;
  private String participantInstanceId;
  private ParticipantKind participantKind;
  private String componentType;
  @Builder.Default private Set<ParticipantCapability> capabilities = Set.of();
  @Builder.Default private Set<SecurityMode> supportedModes = Set.of();
  private String namespace;
  private long startedAt;
  private long lastSeenAt;
  private long statusExpiresAt;
  private StatusVerificationLevel statusVerificationLevel;
  private ParticipantEffectiveState effectiveState;
  @Builder.Default private boolean readyForDataPlane = false;

  @Builder.Default private List<PolicyMismatchReasonDTO> mismatchReasons = List.of();

  /** The key ID this participant is currently signing messages with. Null if unsigned. */
  @Nullable private String currentSigningKeyId;
}

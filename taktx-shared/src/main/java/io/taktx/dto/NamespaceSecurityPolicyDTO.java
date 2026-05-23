/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Explicit namespace-level security policy contract.
 *
 * <p>This DTO is intentionally additive and does not yet replace the existing {@link
 * GlobalConfigurationDTO} runtime configuration path.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public class NamespaceSecurityPolicyDTO {
  private SecurityMode mode;
  private SecurityActivationState activationState;

  /** Requested policy generation. Preferred over {@link #policyVersion} for new integrations. */
  private Long desiredPolicyVersion;

  /** Canonical digest of the requested policy content. Preferred over {@link #policyHash}. */
  private String desiredPolicyHash;

  /** Currently authoritative policy generation, if any. */
  private Long activePolicyVersion;

  /** Canonical digest for the currently authoritative policy, if any. */
  private String activePolicyHash;

  @Builder.Default
  private RequiredSigningDTO requiredSigning = RequiredSigningDTO.builder().build();

  @Builder.Default
  private RequiredAuthorizationDTO requiredAuthorization =
      RequiredAuthorizationDTO.builder().build();

  @Builder.Default private boolean trustAnchorRequired = false;

  /** Explicit actor identifier for privileged break-glass downgrade requests, if any. */
  private String breakGlassActor;

  /** Explicit operator-supplied reason for privileged break-glass downgrade requests, if any. */
  private String breakGlassReason;

  /**
   * Legacy/simple alias for the requested policy generation.
   *
   * <p>During the migration slice this stays available so upstream/downstream code can move in
   * smaller steps without losing explicit desired-vs-active identity support.
   */
  private Long policyVersion;

  /** Legacy/simple alias for the requested canonical policy digest. */
  private String policyHash;
}

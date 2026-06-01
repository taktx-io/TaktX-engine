/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventSeverity;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.ParticipantStatusStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.NamespaceSecurityPolicySupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.Map;

/**
 * Compatibility façade for namespace security policy updates after the shared policy contract was
 * reduced to a single authoritative mode/version/hash identity.
 */
@ApplicationScoped
public class NamespaceSecurityPolicyActivationService {

  static final String ACTIVATION_TIMEOUT_CODE = SecurityPostureIssueCodes.ACTIVATION_TIMEOUT;
  static final String POLICY_REJECTION_CODE = SecurityPostureIssueCodes.POLICY_REJECTION;
  static final String READINESS_MISMATCH_CODE = SecurityPostureIssueCodes.READINESS_MISMATCH;
  static final String BREAK_GLASS_DOWNGRADE_CODE = SecurityPostureIssueCodes.BREAK_GLASS_DOWNGRADE;
  static final String BREAK_GLASS_DOWNGRADE_REJECTED_CODE =
      SecurityPostureIssueCodes.BREAK_GLASS_DOWNGRADE_REJECTED;
  public static final String INVALID_POLICY_MUTATION_CODE =
      SecurityPostureIssueCodes.INVALID_POLICY_MUTATION;

  private final TaktConfiguration configuration;
  private final NamespaceSecurityPolicyStore namespaceSecurityPolicyStore;
  @SuppressWarnings("unused")
  private final ParticipantStatusStore participantStatusStore;
  private final SecurityEventPublisher securityEventPublisher;
  private final Clock clock;
  @SuppressWarnings("unused")
  private final long activationTimeoutMs;

  @Inject
  public NamespaceSecurityPolicyActivationService(
      TaktConfiguration configuration,
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      ParticipantStatusStore participantStatusStore,
      SecurityEventPublisher securityEventPublisher,
      Clock clock) {
    this(
        configuration,
        namespaceSecurityPolicyStore,
        participantStatusStore,
        securityEventPublisher,
        clock,
        configuration.getSecurityPolicyActivationTimeoutMs());
  }

  NamespaceSecurityPolicyActivationService(
      TaktConfiguration configuration,
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      ParticipantStatusStore participantStatusStore,
      SecurityEventPublisher securityEventPublisher,
      Clock clock,
      long activationTimeoutMs) {
    this.configuration = configuration;
    this.namespaceSecurityPolicyStore = namespaceSecurityPolicyStore;
    this.participantStatusStore = participantStatusStore;
    this.securityEventPublisher = securityEventPublisher;
    this.clock = clock;
    this.activationTimeoutMs = activationTimeoutMs;
  }

  public synchronized void onPolicyUpdated(NamespaceSecurityPolicyDTO policy) {
    namespaceSecurityPolicyStore.update(NamespaceSecurityPolicySupport.requireValid(policy));
  }

  public synchronized void onPolicyCleared() {
    namespaceSecurityPolicyStore.clear();
  }

  public synchronized void onParticipantStatusesChanged() {
    // Activation convergence is no longer modeled in the reduced policy contract.
  }

  public synchronized void onRejectedPolicyMutation(String reason, String recordKey) {
    if (securityEventPublisher == null) {
      return;
    }
    NamespaceSecurityPolicyDTO authoritativePolicy = namespaceSecurityPolicyStore.getAuthoritativePolicy();
    securityEventPublisher.publish(
        SecurityEventDTO.builder()
            .eventType(SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED)
            .severity(SecurityEventSeverity.ERROR)
            .occurredAtMs(clock.millis())
            .namespace(configuration.getNamespace())
            .participantId(participantId())
            .participantInstanceId(participantInstanceId())
            .desiredPolicyVersion(
                authoritativePolicy != null ? authoritativePolicy.getPolicyVersion() : null)
            .desiredPolicyHash(authoritativePolicy != null ? authoritativePolicy.getPolicyHash() : null)
            .activePolicyVersion(
                authoritativePolicy != null ? authoritativePolicy.getPolicyVersion() : null)
            .activePolicyHash(authoritativePolicy != null ? authoritativePolicy.getPolicyHash() : null)
            .code(INVALID_POLICY_MUTATION_CODE)
            .message(reason)
            .metadata(
                Map.of(
                    "recordKey", recordKey == null ? "<null>" : recordKey,
                    "reason", reason == null ? "unknown" : reason))
            .build());
  }

  public synchronized void reevaluate() {
    // No-op: there is no multi-stage activation lifecycle in the simplified model.
  }

  private String participantId() {
    return configuration.getTenantId() + "." + configuration.getNamespace() + ".engine";
  }

  private String participantInstanceId() {
    return configuration.getTenantId()
        + "."
        + configuration.getNamespace()
        + "@"
        + configuration.getHost()
        + ":"
        + configuration.getPort();
  }
}

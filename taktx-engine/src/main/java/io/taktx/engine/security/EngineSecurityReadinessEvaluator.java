/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.ParticipantStatusSupport;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Evaluates the engine's current readiness against the explicit namespace security policy contract.
 *
 * <p>This first slice intentionally limits itself to local deployment/runtime facts that the engine
 * can assert without activation coordination.
 */
@ApplicationScoped
@Slf4j
public class EngineSecurityReadinessEvaluator {

  static final String POLICY_NOT_ACTIVE = "POLICY_NOT_ACTIVE";
  static final String TRUST_ANCHOR_MISSING = SecurityPostureIssueCodes.TRUST_ANCHOR_MISSING;
  static final String ENGINE_SIGNING_UNAVAILABLE = "ENGINE_SIGNING_UNAVAILABLE";
  static final String POLICY_MARKED_MISCONFIGURED = "POLICY_MARKED_MISCONFIGURED";
  static final long STATUS_TTL_MS = 30_000L;
  private static final java.util.Set<ParticipantCapability> ENGINE_CAPABILITIES =
      java.util.Set.of(ParticipantCapability.ENFORCER, ParticipantCapability.SECURITY_OBSERVER);

  private final TaktConfiguration configuration;
  private final NamespaceSecurityPolicyStore namespaceSecurityPolicyStore;
  private final MessageSigningService messageSigningService;
  private final Clock clock;
  private final long startedAtMs;

  public EngineSecurityReadinessEvaluator(
      TaktConfiguration configuration,
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      MessageSigningService messageSigningService,
      Clock clock) {
    this.configuration = configuration;
    this.namespaceSecurityPolicyStore = namespaceSecurityPolicyStore;
    this.messageSigningService = messageSigningService;
    this.clock = clock;
    this.startedAtMs = clock.millis();
  }

  public ParticipantStatusDTO evaluateCurrentStatus() {
    NamespaceSecurityPolicyDTO currentPolicy = namespaceSecurityPolicyStore.get();
    NamespaceSecurityPolicyDTO policy = namespaceSecurityPolicyStore.getAuthoritativePolicy();
    messageSigningService.ensureSigningPreparationIfNeeded();
    long nowMs = clock.millis();
    List<PolicyMismatchReasonDTO> mismatchReasons = new ArrayList<>();

    ParticipantEffectiveState effectiveState = ParticipantEffectiveState.READY;
    boolean readyForDataPlane = true;
    Long observedPolicyVersion = null;
    String observedPolicyHash = null;

    if (policy != null) {
      observedPolicyVersion = policy.getActivePolicyVersion();
      observedPolicyHash = policy.getActivePolicyHash();

      if (policy.getMode() == SecurityMode.MISCONFIGURED_SECURITY) {
        effectiveState = ParticipantEffectiveState.MISMATCH;
        readyForDataPlane = false;
        mismatchReasons.add(
            mismatchReason(
                POLICY_MARKED_MISCONFIGURED,
                "Policy mode is MISCONFIGURED_SECURITY and therefore cannot be treated as ready"));
      }

      if (policy.isTrustAnchorRequired()
          && (configuration.getPlatformPublicKey() == null
              || configuration.getPlatformPublicKey().isBlank())) {
        effectiveState = ParticipantEffectiveState.MISMATCH;
        readyForDataPlane = false;
        mismatchReasons.add(
            mismatchReason(
                TRUST_ANCHOR_MISSING,
                "Namespace requires anchored trust but no platform public key is configured"));
      }

      if (policy.getRequiredSigning() != null
          && policy.getRequiredSigning().isEngineOutbound()
          && (messageSigningService.getKeyId() == null
              || !messageSigningService.isPublicKeyPublished())) {
        effectiveState = ParticipantEffectiveState.MISMATCH;
        readyForDataPlane = false;
        mismatchReasons.add(
            mismatchReason(
                ENGINE_SIGNING_UNAVAILABLE,
                "Namespace requires engine outbound signing but the engine signing key is not yet available and published"));
      }
    } else if (currentPolicy != null
        && currentPolicy.getActivationState() != SecurityActivationState.ACTIVE) {
      log.debug(
          "Namespace security policy pending activation; continuing to evaluate readiness under current authoritative behavior: activationState={} desiredPolicyVersion={} desiredPolicyHash={}",
          currentPolicy.getActivationState(),
          currentPolicy.getDesiredPolicyVersion(),
          currentPolicy.getDesiredPolicyHash());
    }

    return ParticipantStatusDTO.builder()
        .participantId(participantId())
        .participantInstanceId(participantInstanceId())
        .participantKind(ParticipantKind.ENGINE)
        .componentType("engine")
        .capabilities(ENGINE_CAPABILITIES)
        .supportedModes(ParticipantStatusSupport.supportedModesForCapabilities(ENGINE_CAPABILITIES))
        .namespace(configuration.getNamespace())
        .startedAt(startedAtMs)
        .lastSeenAt(nowMs)
        .statusExpiresAt(nowMs + STATUS_TTL_MS)
        .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
        .effectiveState(effectiveState)
        .readyForDataPlane(readyForDataPlane)
        .observedPolicyVersion(observedPolicyVersion)
        .observedPolicyHash(observedPolicyHash)
        .mismatchReasons(List.copyOf(mismatchReasons))
        .build();
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
        + configuration.getPort()
        + "#"
        + ProcessHandle.current().pid();
  }

  private static PolicyMismatchReasonDTO mismatchReason(String code, String message) {
    return PolicyMismatchReasonDTO.builder().code(code).message(message).build();
  }
}

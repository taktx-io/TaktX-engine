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
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.TaktConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Evaluates the engine's current readiness against the authoritative namespace security policy. */
@ApplicationScoped
public class EngineSecurityReadinessEvaluator {

  static final String TRUST_ANCHOR_MISSING = SecurityPostureIssueCodes.TRUST_ANCHOR_MISSING;
  static final String ENGINE_SIGNING_UNAVAILABLE = "ENGINE_SIGNING_UNAVAILABLE";
  static final String ENGINE_ANCHORED_TRUST_UNAVAILABLE = "ENGINE_ANCHORED_TRUST_UNAVAILABLE";
  static final long STATUS_TTL_MS = 30_000L;
  private static final Set<ParticipantCapability> ENGINE_CAPABILITIES =
      Set.of(ParticipantCapability.ENFORCER, ParticipantCapability.SECURITY_OBSERVER);

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
    NamespaceSecurityPolicyDTO policy = namespaceSecurityPolicyStore.getAuthoritativePolicy();
    messageSigningService.ensureSigningPreparationIfNeeded();
    boolean anchoredModeSupported = supportsAnchoredModeNow();
    long nowMs = clock.millis();
    List<PolicyMismatchReasonDTO> mismatchReasons = new ArrayList<>();

    ParticipantEffectiveState effectiveState = ParticipantEffectiveState.READY;
    boolean readyForDataPlane = true;
    Long observedPolicyVersion = null;
    String observedPolicyHash = null;

    if (policy != null) {
      observedPolicyVersion = policy.getPolicyVersion();
      observedPolicyHash = policy.getPolicyHash();

      if (policy.getMode() == SecurityMode.ANCHORED) {
        if (!hasPlatformTrustAnchorConfigured()) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  TRUST_ANCHOR_MISSING,
                  "Namespace requires anchored trust but no platform public key is configured"));
        }

        if (messageSigningService.getKeyId() == null || !messageSigningService.isPublicKeyPublished()) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  ENGINE_SIGNING_UNAVAILABLE,
                  "Namespace requires anchored posture but the engine signing identity is not yet available and published"));
        }

        if (!anchoredModeSupported) {
          effectiveState = ParticipantEffectiveState.MISMATCH;
          readyForDataPlane = false;
          mismatchReasons.add(
              mismatchReason(
                  ENGINE_ANCHORED_TRUST_UNAVAILABLE,
                  "Namespace requires anchored trust but the engine is not currently configured with stable signing material, a platform public key, and an engine key registration signature"));
        }
      }
    }

    return ParticipantStatusDTO.builder()
        .participantId(participantId())
        .participantInstanceId(participantInstanceId())
        .participantKind(ParticipantKind.ENGINE)
        .componentType("engine")
        .capabilities(ENGINE_CAPABILITIES)
        .supportedModes(runtimeSupportedModes(anchoredModeSupported))
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

  private Set<SecurityMode> runtimeSupportedModes(boolean anchoredModeSupported) {
    EnumSet<SecurityMode> supportedModes = EnumSet.of(SecurityMode.OPEN);
    if (anchoredModeSupported) {
      supportedModes.add(SecurityMode.ANCHORED);
    }
    return Set.copyOf(supportedModes);
  }

  private boolean supportsAnchoredModeNow() {
    if (!messageSigningService.hasPublishableSigningIdentity()) {
      return false;
    }
    return hasStableSigningSourceConfigured()
        && hasPlatformTrustAnchorConfigured()
        && hasEngineKeyRegistrationSignatureConfigured();
  }

  private boolean hasStableSigningSourceConfigured() {
    String sourceType = configuration.getSigningIdentitySourceType();
    if (isBlank(sourceType)) {
      return false;
    }
    return "env".equalsIgnoreCase(sourceType)
        || "environment".equalsIgnoreCase(sourceType)
        || "file".equalsIgnoreCase(sourceType);
  }

  private boolean hasPlatformTrustAnchorConfigured() {
    return !isBlank(configuration.getPlatformPublicKey());
  }

  private boolean hasEngineKeyRegistrationSignatureConfigured() {
    return !isBlank(configuration.getEngineKeyRegistrationSignature());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static PolicyMismatchReasonDTO mismatchReason(String code, String message) {
    return PolicyMismatchReasonDTO.builder().code(code).message(message).build();
  }
}

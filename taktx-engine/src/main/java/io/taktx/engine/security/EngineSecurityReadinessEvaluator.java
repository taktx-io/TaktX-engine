/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.engine.config.TaktConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates the engine's current readiness. With mode resolved at startup the only transient
 * mismatch is the async key-publication window after a cold start in anchored mode.
 */
@ApplicationScoped
public class EngineSecurityReadinessEvaluator {

  static final String ENGINE_SIGNING_UNAVAILABLE = "ENGINE_SIGNING_UNAVAILABLE";
  static final long STATUS_TTL_MS = 30_000L;
  private static final Set<ParticipantCapability> ENGINE_CAPABILITIES =
      Set.of(ParticipantCapability.ENFORCER, ParticipantCapability.SECURITY_OBSERVER);

  private final TaktConfiguration configuration;
  private final MessageSigningService messageSigningService;
  private final Clock clock;
  private final long startedAtMs;
  private final boolean anchored;

  @Inject
  public EngineSecurityReadinessEvaluator(
      TaktConfiguration configuration,
      MessageSigningService messageSigningService,
      Clock clock) {
    this.configuration = configuration;
    this.messageSigningService = messageSigningService;
    this.clock = clock;
    this.startedAtMs = clock.millis();
    this.anchored = configuration.isAnchored();
  }

  public ParticipantStatusDTO evaluateCurrentStatus() {
    messageSigningService.ensureSigningPreparationIfNeeded();
    boolean anchoredModeSupported = supportsAnchoredModeNow();
    long nowMs = clock.millis();
    List<PolicyMismatchReasonDTO> mismatchReasons = new ArrayList<>();

    ParticipantEffectiveState effectiveState = ParticipantEffectiveState.READY;
    boolean readyForDataPlane = true;

    if (anchored) {
      // Only transient reason: own key not yet published after cold start.
      if (messageSigningService.getKeyId() == null || !messageSigningService.isPublicKeyPublished()) {
        effectiveState = ParticipantEffectiveState.MISMATCH;
        readyForDataPlane = false;
        mismatchReasons.add(
            mismatchReason(
                ENGINE_SIGNING_UNAVAILABLE,
                "Anchored mode active but the engine signing key has not been published yet"));
      }
    }

    String signingKeyId = messageSigningService.getKeyId();
    return ParticipantStatusDTO.builder()
        .participantId(participantId(signingKeyId))
        .participantInstanceId(participantInstanceId())
        .participantKind(ParticipantKind.ENGINE)
        .componentType(componentType(signingKeyId))
        .capabilities(ENGINE_CAPABILITIES)
        .supportedModes(runtimeSupportedModes(anchoredModeSupported))
        .namespace(configuration.getNamespace())
        .startedAt(startedAtMs)
        .lastSeenAt(nowMs)
        .statusExpiresAt(nowMs + STATUS_TTL_MS)
        .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
        .effectiveState(effectiveState)
        .readyForDataPlane(readyForDataPlane)
        .mismatchReasons(List.copyOf(mismatchReasons))
        .currentSigningKeyId(signingKeyId)
        .build();
  }

  private String participantId(String signingKeyId) {
    if (signingKeyId != null && !signingKeyId.isBlank()) {
      return signingKeyId;
    }
    return configuration.getNamespace() + ".engine";
  }

  private static String componentType(String signingKeyId) {
    if (signingKeyId != null && !signingKeyId.isBlank()) {
      return signingKeyId.split("-", 2)[0];
    }
    return "engine";
  }

  private String participantInstanceId() {
    return configuration.getNamespace()
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
    return anchored && messageSigningService.hasPublishableSigningIdentity();
  }

  private static PolicyMismatchReasonDTO mismatchReason(String code, String message) {
    return PolicyMismatchReasonDTO.builder().code(code).message(message).build();
  }
}

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
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventSeverity;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.ParticipantStatusStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.NamespaceSecurityPolicySupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** Coordinates first-slice namespace security policy activation lifecycle decisions. */
@ApplicationScoped
@Slf4j
public class NamespaceSecurityPolicyActivationService {

  static final String ACTIVATION_TIMEOUT_CODE = "ACTIVATION_TIMEOUT";
  static final String POLICY_REJECTION_CODE = "POLICY_REJECTION";
  static final String READINESS_MISMATCH_CODE = "READINESS_MISMATCH";
  static final String BREAK_GLASS_DOWNGRADE_CODE = "BREAK_GLASS_DOWNGRADE";
  static final String BREAK_GLASS_DOWNGRADE_REJECTED_CODE = "BREAK_GLASS_DOWNGRADE_REJECTED";
  public static final String INVALID_POLICY_MUTATION_CODE = "INVALID_POLICY_MUTATION";

  private static final Set<ParticipantCapability> REQUIRED_ACTIVATION_CAPABILITIES =
      EnumSet.of(ParticipantCapability.ENFORCER);

  private final TaktConfiguration configuration;
  private final NamespaceSecurityPolicyStore namespaceSecurityPolicyStore;
  private final ParticipantStatusStore participantStatusStore;
  private final SecurityEventPublisher securityEventPublisher;
  private final Clock clock;
  private final long activationTimeoutMs;
  private final AtomicReference<String> lastActiveDriftFingerprint = new AtomicReference<>(null);

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
    NamespaceSecurityPolicyDTO validated = NamespaceSecurityPolicySupport.requireValid(policy);
    NamespaceSecurityPolicyDTO current = namespaceSecurityPolicyStore.get();
    NamespaceSecurityPolicyDTO previousActive = namespaceSecurityPolicyStore.getActivePolicy();

    if (validated.getActivationState() == SecurityActivationState.ACTIVE) {
      namespaceSecurityPolicyStore.setCurrentPolicy(validated);
      namespaceSecurityPolicyStore.setActivePolicy(validated);
      namespaceSecurityPolicyStore.setValidationStartedAtMs(null);
      return;
    }

    if (current != null
        && current.getActivationState() == SecurityActivationState.ACTIVE
        && sameDesiredIdentity(current, validated)) {
      return;
    }

    Long startedAtMs = clock.millis();
    if (current != null
        && current.getActivationState() == SecurityActivationState.VALIDATING
        && sameDesiredIdentity(current, validated)
        && namespaceSecurityPolicyStore.getValidationStartedAtMs() != null) {
      startedAtMs = namespaceSecurityPolicyStore.getValidationStartedAtMs();
    }

    if (isBreakGlassDowngrade(previousActive, validated)) {
      if (isBlank(validated.getBreakGlassActor()) || isBlank(validated.getBreakGlassReason())) {
        publish(
            SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED,
            SecurityEventSeverity.ERROR,
            BREAK_GLASS_DOWNGRADE_REJECTED_CODE,
            "Requested downgrade was rejected because explicit break-glass actor/reason metadata was not provided",
            withPreservedActiveIdentity(validated, previousActive),
            Map.of(
                "previousMode", previousActive.getMode().name(),
                "requestedMode", String.valueOf(validated.getMode()),
                "requiresBreakGlass", Boolean.TRUE.toString()));
        publish(
            SecurityEventType.POLICY_REJECTION,
            SecurityEventSeverity.ERROR,
            POLICY_REJECTION_CODE,
            "Requested downgrade was rejected because privileged break-glass metadata was missing",
            withPreservedActiveIdentity(validated, previousActive),
            Map.of(
                "previousMode", previousActive.getMode().name(),
                "requestedMode", String.valueOf(validated.getMode()),
                "requiresBreakGlass", Boolean.TRUE.toString()));
        rollbackToPreviousActive();
        return;
      }

      publish(
          SecurityEventType.POLICY_DOWNGRADE,
          SecurityEventSeverity.CRITICAL,
          BREAK_GLASS_DOWNGRADE_CODE,
          "Requested privileged break-glass downgrade observed and allowed to proceed through activation lifecycle",
          withPreservedActiveIdentity(validated, previousActive),
          Map.of(
              "previousMode", previousActive.getMode().name(),
              "requestedMode", String.valueOf(validated.getMode()),
              "breakGlassActor", validated.getBreakGlassActor(),
              "breakGlassReason", validated.getBreakGlassReason()));
    }

    NamespaceSecurityPolicyDTO validatingPolicy =
        withPreservedActiveIdentity(validated, previousActive).toBuilder()
            .activationState(SecurityActivationState.VALIDATING)
            .build();

    namespaceSecurityPolicyStore.setCurrentPolicy(validatingPolicy);
    namespaceSecurityPolicyStore.setValidationStartedAtMs(startedAtMs);
    reevaluate();
  }

  public synchronized void onPolicyCleared() {
    namespaceSecurityPolicyStore.clear();
  }

  public synchronized void onParticipantStatusesChanged() {
    reevaluate();
  }

  public synchronized void onRejectedPolicyMutation(String reason, String recordKey) {
    if (securityEventPublisher == null) {
      return;
    }
    securityEventPublisher.publish(
        SecurityEventDTO.builder()
            .eventType(SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED)
            .severity(SecurityEventSeverity.ERROR)
            .occurredAtMs(clock.millis())
            .namespace(configuration.getNamespace())
            .participantId(participantId())
            .participantInstanceId(participantInstanceId())
            .activePolicyVersion(
                activePolicyVersion(namespaceSecurityPolicyStore.getActivePolicy()))
            .activePolicyHash(activePolicyHash(namespaceSecurityPolicyStore.getActivePolicy()))
            .code(INVALID_POLICY_MUTATION_CODE)
            .message(reason)
            .metadata(
                Map.of(
                    "recordKey", recordKey == null ? "<null>" : recordKey,
                    "reason", reason == null ? "unknown" : reason))
            .build());
  }

  public synchronized void reevaluate() {
    NamespaceSecurityPolicyDTO current = namespaceSecurityPolicyStore.get();
    if (current == null) {
      lastActiveDriftFingerprint.set(null);
      return;
    }

    if (current.getActivationState() == SecurityActivationState.ACTIVE) {
      namespaceSecurityPolicyStore.setActivePolicy(current);
      namespaceSecurityPolicyStore.setValidationStartedAtMs(null);
      emitPostActivationDriftIfNeeded(current, clock.millis());
      return;
    }

    if (current.getActivationState() != SecurityActivationState.VALIDATING
        && current.getActivationState() != SecurityActivationState.REQUESTED) {
      return;
    }

    long nowMs = clock.millis();
    long startedAtMs =
        namespaceSecurityPolicyStore.getValidationStartedAtMs() != null
            ? namespaceSecurityPolicyStore.getValidationStartedAtMs()
            : nowMs;
    namespaceSecurityPolicyStore.setValidationStartedAtMs(startedAtMs);

    ConvergenceAssessment assessment = assess(current, nowMs);

    if (nowMs - startedAtMs >= activationTimeoutMs) {
      rejectForTimeout(current, assessment, nowMs, startedAtMs);
      return;
    }

    if (assessment.outcome() == ConvergenceOutcome.SUCCESS) {
      NamespaceSecurityPolicyDTO activePolicy =
          current.toBuilder()
              .activationState(SecurityActivationState.ACTIVE)
              .activePolicyVersion(current.getDesiredPolicyVersion())
              .activePolicyHash(current.getDesiredPolicyHash())
              .build();
      namespaceSecurityPolicyStore.setCurrentPolicy(activePolicy);
      namespaceSecurityPolicyStore.setActivePolicy(activePolicy);
      namespaceSecurityPolicyStore.setValidationStartedAtMs(null);
      log.info(
          "Namespace security policy activated: desiredPolicyVersion={} desiredPolicyHash={}",
          activePolicy.getDesiredPolicyVersion(),
          activePolicy.getDesiredPolicyHash());
      return;
    }

    if (assessment.outcome() == ConvergenceOutcome.FAILURE) {
      rejectForMismatch(current, assessment, nowMs);
    }
  }

  private ConvergenceAssessment assess(NamespaceSecurityPolicyDTO policy, long nowMs) {
    Long expectedPolicyVersion =
        policy.getActivationState() == SecurityActivationState.ACTIVE
            ? policy.getActivePolicyVersion()
            : policy.getDesiredPolicyVersion();
    String expectedPolicyHash =
        policy.getActivationState() == SecurityActivationState.ACTIVE
            ? policy.getActivePolicyHash()
            : policy.getDesiredPolicyHash();
    Map<String, ParticipantStatusDTO> currentStatuses =
        participantStatusStore.currentSnapshot(REQUIRED_ACTIVATION_CAPABILITIES, nowMs);

    List<String> missingRequiredCapabilities = new ArrayList<>();
    for (ParticipantCapability requiredCapability : REQUIRED_ACTIVATION_CAPABILITIES) {
      boolean present =
          currentStatuses.values().stream()
              .anyMatch(status -> status.getCapabilities().contains(requiredCapability));
      if (!present) {
        missingRequiredCapabilities.add(requiredCapability.name());
      }
    }
    if (!missingRequiredCapabilities.isEmpty()) {
      return new ConvergenceAssessment(
          ConvergenceOutcome.PENDING,
          missingRequiredCapabilities,
          List.of(),
          List.of(),
          currentStatuses.size());
    }

    List<String> notReadyParticipants = new ArrayList<>();
    List<String> policyMismatchParticipants = new ArrayList<>();
    for (ParticipantStatusDTO status : currentStatuses.values()) {
      if (status.getEffectiveState() != ParticipantEffectiveState.READY
          || !status.isReadyForDataPlane()) {
        notReadyParticipants.add(status.getParticipantInstanceId());
      }
      if (!Objects.equals(expectedPolicyVersion, status.getObservedPolicyVersion())
          || !Objects.equals(expectedPolicyHash, status.getObservedPolicyHash())) {
        policyMismatchParticipants.add(status.getParticipantInstanceId());
      }
    }

    if (!notReadyParticipants.isEmpty() || !policyMismatchParticipants.isEmpty()) {
      return new ConvergenceAssessment(
          ConvergenceOutcome.FAILURE,
          List.of(),
          List.copyOf(notReadyParticipants),
          List.copyOf(policyMismatchParticipants),
          currentStatuses.size());
    }

    return new ConvergenceAssessment(
        ConvergenceOutcome.SUCCESS, List.of(), List.of(), List.of(), currentStatuses.size());
  }

  private void emitPostActivationDriftIfNeeded(
      NamespaceSecurityPolicyDTO activePolicy, long nowMs) {
    ConvergenceAssessment assessment = assess(activePolicy, nowMs);
    if (assessment.outcome() == ConvergenceOutcome.SUCCESS) {
      lastActiveDriftFingerprint.set(null);
      return;
    }

    Map<String, String> metadata =
        new LinkedHashMap<>(metadataForAssessment(assessment, nowMs, nowMs));
    metadata.put("phase", SecurityActivationState.ACTIVE.name());
    metadata.put("postActivationDrift", Boolean.TRUE.toString());
    String fingerprint =
        String.join(
            "|",
            metadata.getOrDefault("missingRequiredCapabilities", ""),
            metadata.getOrDefault("notReadyParticipants", ""),
            metadata.getOrDefault("policyMismatchParticipants", ""),
            String.valueOf(activePolicy.getActivePolicyVersion()),
            String.valueOf(activePolicy.getActivePolicyHash()));
    if (fingerprint.equals(lastActiveDriftFingerprint.get())) {
      return;
    }

    publish(
        SecurityEventType.READINESS_MISMATCH,
        SecurityEventSeverity.WARNING,
        READINESS_MISMATCH_CODE,
        "Required participants diverged from the ACTIVE policy identity or readiness state after activation",
        activePolicy,
        Map.copyOf(metadata));
    lastActiveDriftFingerprint.set(fingerprint);
  }

  private void rejectForTimeout(
      NamespaceSecurityPolicyDTO policy,
      ConvergenceAssessment assessment,
      long nowMs,
      long startedAtMs) {
    publish(
        SecurityEventType.ACTIVATION_TIMEOUT,
        SecurityEventSeverity.ERROR,
        ACTIVATION_TIMEOUT_CODE,
        "Requested policy remained in VALIDATING beyond the configured activation timeout",
        policy,
        metadataForAssessment(assessment, startedAtMs, nowMs));
    publish(
        SecurityEventType.POLICY_REJECTION,
        SecurityEventSeverity.ERROR,
        POLICY_REJECTION_CODE,
        "Requested policy was rejected after activation timed out and the previous ACTIVE policy was preserved",
        policy,
        metadataForAssessment(assessment, startedAtMs, nowMs));
    rollbackToPreviousActive();
  }

  private void rejectForMismatch(
      NamespaceSecurityPolicyDTO policy, ConvergenceAssessment assessment, long nowMs) {
    Map<String, String> metadata = metadataForAssessment(assessment, nowMs, nowMs);
    publish(
        SecurityEventType.READINESS_MISMATCH,
        SecurityEventSeverity.WARNING,
        READINESS_MISMATCH_CODE,
        "Required participants did not converge on the requested policy identity or readiness state",
        policy,
        metadata);
    publish(
        SecurityEventType.POLICY_REJECTION,
        SecurityEventSeverity.ERROR,
        POLICY_REJECTION_CODE,
        "Requested policy was rejected because required participants could not converge",
        policy,
        metadata);
    rollbackToPreviousActive();
  }

  private void rollbackToPreviousActive() {
    NamespaceSecurityPolicyDTO previousActive = namespaceSecurityPolicyStore.getActivePolicy();
    namespaceSecurityPolicyStore.setValidationStartedAtMs(null);
    if (previousActive != null) {
      namespaceSecurityPolicyStore.setCurrentPolicy(previousActive);
      log.info(
          "Namespace security policy activation failed; previous ACTIVE policy preserved: activePolicyVersion={} activePolicyHash={}",
          previousActive.getActivePolicyVersion(),
          previousActive.getActivePolicyHash());
      return;
    }
    namespaceSecurityPolicyStore.clearCurrentPolicy();
    log.info(
        "Namespace security policy activation failed; no previous ACTIVE policy was available");
  }

  private void publish(
      SecurityEventType eventType,
      SecurityEventSeverity severity,
      String code,
      String message,
      NamespaceSecurityPolicyDTO policy,
      Map<String, String> metadata) {
    if (securityEventPublisher == null) {
      return;
    }
    securityEventPublisher.publish(
        SecurityEventDTO.builder()
            .eventType(eventType)
            .severity(severity)
            .occurredAtMs(clock.millis())
            .namespace(configuration.getNamespace())
            .participantId(participantId())
            .participantInstanceId(participantInstanceId())
            .desiredPolicyVersion(policy.getDesiredPolicyVersion())
            .desiredPolicyHash(policy.getDesiredPolicyHash())
            .activePolicyVersion(
                activePolicyVersion(namespaceSecurityPolicyStore.getActivePolicy()))
            .activePolicyHash(activePolicyHash(namespaceSecurityPolicyStore.getActivePolicy()))
            .code(code)
            .message(message)
            .metadata(metadata)
            .build());
  }

  private Map<String, String> metadataForAssessment(
      ConvergenceAssessment assessment, long startedAtMs, long nowMs) {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("timeoutMs", Long.toString(activationTimeoutMs));
    metadata.put("validationStartedAtMs", Long.toString(startedAtMs));
    metadata.put("evaluatedAtMs", Long.toString(nowMs));
    metadata.put(
        "requiredActivationCapabilities",
        REQUIRED_ACTIVATION_CAPABILITIES.stream()
            .map(Enum::name)
            .sorted()
            .collect(Collectors.joining(",")));
    metadata.put(
        "observedRequiredParticipantCount",
        Integer.toString(assessment.observedRequiredParticipantCount()));
    if (!assessment.missingRequiredCapabilities().isEmpty()) {
      metadata.put(
          "missingRequiredCapabilities",
          String.join(",", assessment.missingRequiredCapabilities()));
    }
    if (!assessment.notReadyParticipants().isEmpty()) {
      metadata.put("notReadyParticipants", String.join(",", assessment.notReadyParticipants()));
    }
    if (!assessment.policyMismatchParticipants().isEmpty()) {
      metadata.put(
          "policyMismatchParticipants", String.join(",", assessment.policyMismatchParticipants()));
    }
    return Map.copyOf(metadata);
  }

  private NamespaceSecurityPolicyDTO withPreservedActiveIdentity(
      NamespaceSecurityPolicyDTO requestedPolicy, NamespaceSecurityPolicyDTO previousActive) {
    if (previousActive == null) {
      return requestedPolicy.toBuilder().activePolicyVersion(null).activePolicyHash(null).build();
    }
    return requestedPolicy.toBuilder()
        .activePolicyVersion(activePolicyVersion(previousActive))
        .activePolicyHash(activePolicyHash(previousActive))
        .build();
  }

  private static Long activePolicyVersion(NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      return null;
    }
    return policy.getActivePolicyVersion() != null
        ? policy.getActivePolicyVersion()
        : policy.getDesiredPolicyVersion();
  }

  private static String activePolicyHash(NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      return null;
    }
    return policy.getActivePolicyHash() != null
        ? policy.getActivePolicyHash()
        : policy.getDesiredPolicyHash();
  }

  private static boolean sameDesiredIdentity(
      NamespaceSecurityPolicyDTO left, NamespaceSecurityPolicyDTO right) {
    return Objects.equals(left.getDesiredPolicyVersion(), right.getDesiredPolicyVersion())
        && Objects.equals(left.getDesiredPolicyHash(), right.getDesiredPolicyHash());
  }

  private static boolean isBreakGlassDowngrade(
      NamespaceSecurityPolicyDTO previousActive, NamespaceSecurityPolicyDTO requested) {
    if (previousActive == null
        || previousActive.getMode() == null
        || requested == null
        || requested.getMode() == null) {
      return false;
    }
    return securityStrength(requested.getMode()) < securityStrength(previousActive.getMode());
  }

  private static int securityStrength(SecurityMode mode) {
    return switch (mode) {
      case COMMUNITY_OPEN -> 0;
      case COMMUNITY_SECURED -> 1;
      case ANCHORED_SECURED -> 2;
      case MISCONFIGURED_SECURITY -> -1;
    };
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
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

  private enum ConvergenceOutcome {
    SUCCESS,
    PENDING,
    FAILURE
  }

  private record ConvergenceAssessment(
      ConvergenceOutcome outcome,
      List<String> missingRequiredCapabilities,
      List<String> notReadyParticipants,
      List<String> policyMismatchParticipants,
      int observedRequiredParticipantCount) {}
}

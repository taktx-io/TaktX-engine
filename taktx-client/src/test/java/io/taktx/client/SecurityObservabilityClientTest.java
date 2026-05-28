/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventSeverity;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.StatusVerificationLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SecurityObservabilityClientTest {

  @Test
  void observedPolicySnapshot_prefersAuthoritativePolicyForEffectiveHelpers() {
    ObservedPolicySnapshot snapshot =
        new ObservedPolicySnapshot(
            requestedPolicy(11L), activePolicy(7L, SecurityMode.ANCHORED_SECURED));

    assertThat(snapshot.hasCurrentPolicy()).isTrue();
    assertThat(snapshot.hasAuthoritativePolicy()).isTrue();
    assertThat(snapshot.currentActivationState()).isEqualTo(SecurityActivationState.REQUESTED);
    assertThat(snapshot.effectiveMode()).isEqualTo(SecurityMode.ANCHORED_SECURED);
    assertThat(snapshot.effectivePolicyVersion()).isEqualTo(7L);
    assertThat(snapshot.effectivePolicyHash()).isEqualTo("active-7");
  }

  @Test
  void registerNamespaceSecurityPolicyConsumer_replaysCurrentSnapshotAndUpdates() {
    TestHarness harness = new TestHarness();
    harness.observedPolicySnapshot.set(new ObservedPolicySnapshot(requestedPolicy(4L), null));

    List<ObservedPolicySnapshot> observedSnapshots = new ArrayList<>();
    harness.client.registerNamespaceSecurityPolicyConsumer(observedSnapshots::add);

    assertThat(observedSnapshots).hasSize(1);
    assertThat(observedSnapshots.getFirst().effectivePolicyVersion()).isEqualTo(4L);

    harness.observedPolicySnapshot.set(
        new ObservedPolicySnapshot(requestedPolicy(5L), activePolicy(5L)));
    harness.emitPolicySnapshot();

    assertThat(observedSnapshots).hasSize(2);
    assertThat(observedSnapshots.getLast().effectivePolicyVersion()).isEqualTo(5L);
    assertThat(observedSnapshots.getLast().effectivePolicyHash()).isEqualTo("active-5");
  }

  @Test
  void registerParticipantStatusConsumer_replaysCurrentSnapshotAndUpdates() {
    TestHarness harness = new TestHarness();
    harness.participantStatuses.set(
        Map.of(
            "engine#1",
            participantStatus(
                "engine#1",
                Set.of(ParticipantCapability.ENFORCER, ParticipantCapability.SECURITY_OBSERVER))));

    List<Map<String, ParticipantStatusDTO>> observedSnapshots = new ArrayList<>();
    harness.client.registerParticipantStatusConsumer(
        snapshot -> observedSnapshots.add(new LinkedHashMap<>(snapshot)));

    assertThat(observedSnapshots).hasSize(1);
    assertThat(observedSnapshots.getFirst()).containsOnlyKeys("engine#1");

    harness.participantStatuses.set(
        Map.of(
            "engine#1",
            participantStatus("engine#1", Set.of(ParticipantCapability.ENFORCER)),
            "client#9",
            participantStatus(
                "client#9", Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT))));
    harness.emitParticipantStatuses();

    assertThat(observedSnapshots).hasSize(2);
    assertThat(observedSnapshots.getLast()).containsOnlyKeys("client#9", "engine#1");
  }

  @Test
  void registerSecurityEventConsumer_replaysRecentHistoryAndNewEvents() {
    TestHarness harness = new TestHarness();
    harness.securityEvents.set(
        List.of(securityEvent("POLICY_REQUESTED", 1L), securityEvent("POLICY_ACTIVE", 2L)));

    List<SecurityEventDTO> observedEvents = new ArrayList<>();
    harness.client.registerSecurityEventConsumer(observedEvents::add);

    assertThat(observedEvents)
        .extracting(SecurityEventDTO::getCode)
        .containsExactly("POLICY_REQUESTED", "POLICY_ACTIVE");

    SecurityEventDTO blocked = securityEvent("DATA_PLANE_BLOCKED", 3L);
    harness.securityEvents.set(
        List.of(
            securityEvent("POLICY_REQUESTED", 1L), securityEvent("POLICY_ACTIVE", 2L), blocked));
    harness.emitSecurityEvent(blocked);

    assertThat(observedEvents)
        .extracting(SecurityEventDTO::getCode)
        .containsExactly("POLICY_REQUESTED", "POLICY_ACTIVE", "DATA_PLANE_BLOCKED");
  }

  @Test
  void getPostureSnapshot_assemblesPolicyStatusesMismatchesAndEvents() {
    TestHarness harness = new TestHarness();
    harness.observedPolicySnapshot.set(
        new ObservedPolicySnapshot(
            requestedPolicy(12L), activePolicy(11L, SecurityMode.ANCHORED_SECURED)));
    harness.participantStatuses.set(
        Map.of(
            "client#7",
            participantStatus(
                "client#7",
                Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT),
                List.of(mismatchReason("TRUST_ANCHOR_MISSING", "Platform trust anchor missing")))));
    harness.securityEvents.set(List.of(securityEvent("DATA_PLANE_BLOCKED", 11L)));

    SecurityPostureSnapshot snapshot = harness.client.getPostureSnapshot();

    assertThat(snapshot.effectiveMode()).isEqualTo(SecurityMode.ANCHORED_SECURED);
    assertThat(snapshot.effectivePolicyVersion()).isEqualTo(11L);
    assertThat(snapshot.effectivePolicyHash()).isEqualTo("active-11");
    assertThat(snapshot.participantStatuses()).containsOnlyKeys("client#7");
    assertThat(snapshot.mismatchReasons()).hasSize(1);
    assertThat(snapshot.mismatchReasons().getFirst().participantInstanceId()).isEqualTo("client#7");
    assertThat(snapshot.mismatchReasons().getFirst().mismatchReason().getCode())
        .isEqualTo("TRUST_ANCHOR_MISSING");
    assertThat(snapshot.recentSecurityEvents())
        .extracting(SecurityEventDTO::getCode)
        .containsExactly("DATA_PLANE_BLOCKED");
  }

  @Test
  void getPostureSnapshot_keepsMismatchVisibilitySeparateFromEventHistory() {
    TestHarness harness = new TestHarness();
    harness.observedPolicySnapshot.set(
        new ObservedPolicySnapshot(requestedPolicy(6L), activePolicy(6L)));
    harness.participantStatuses.set(
        Map.of(
            "client#2",
            participantStatus(
                "client#2",
                Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT),
                List.of())));
    harness.securityEvents.set(List.of(securityEvent("DATA_PLANE_BLOCKED", 6L)));

    SecurityPostureSnapshot snapshot = harness.client.getPostureSnapshot();

    assertThat(snapshot.hasRecentSecurityEvents()).isTrue();
    assertThat(snapshot.recentSecurityEvents()).hasSize(1);
    assertThat(snapshot.mismatchReasons()).isEmpty();
    assertThat(snapshot.participantsWithMismatches()).isEmpty();
  }

  @Test
  void snapshotHelpers_defaultToEmptyStateWhenNothingHasBeenObserved() {
    TestHarness harness = new TestHarness();
    SecurityPostureSnapshot postureSnapshot = harness.client.getPostureSnapshot();

    assertThat(harness.client.getObservedPolicySnapshot())
        .isEqualTo(ObservedPolicySnapshot.empty());
    assertThat(harness.client.getParticipantStatusSnapshot()).isEmpty();
    assertThat(harness.client.getRecentSecurityEvents()).isEmpty();
    assertThat(postureSnapshot).isEqualTo(SecurityPostureSnapshot.empty());
    assertThat(harness.initializerCalls.get()).isGreaterThanOrEqualTo(4);
  }

  @Test
  void awaitHelpers_pollUntilPolicyStatusesEventsAndPostureAppear() throws Exception {
    TestHarness harness = new TestHarness();

    Thread updater =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    Thread.sleep(60L);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                  }
                  harness.observedPolicySnapshot.set(
                      new ObservedPolicySnapshot(requestedPolicy(9L), activePolicy(9L)));
                  harness.participantStatuses.set(
                      Map.of(
                          "engine#1",
                          participantStatus(
                              "engine#1",
                              Set.of(ParticipantCapability.ENFORCER),
                              List.of(mismatchReason("ENGINE_SYNC_PENDING", "Waiting for peer")))));
                  harness.securityEvents.set(List.of(securityEvent("POLICY_ACTIVE", 9L)));
                });

    ObservedPolicySnapshot policySnapshot =
        harness.client.awaitObservedPolicy(
            snapshot -> Long.valueOf(9L).equals(snapshot.effectivePolicyVersion()),
            Duration.ofSeconds(1));
    Map<String, ParticipantStatusDTO> statuses =
        harness.client.awaitParticipantStatusSnapshot(
            snapshot -> snapshot.containsKey("engine#1"), Duration.ofSeconds(1));
    SecurityEventDTO event =
        harness.client.awaitSecurityEvent(
            candidate -> "POLICY_ACTIVE".equals(candidate.getCode()), Duration.ofSeconds(1));
    SecurityPostureSnapshot postureSnapshot =
        harness.client.awaitPostureSnapshot(
            snapshot ->
                snapshot.hasMismatchReasons()
                    && snapshot.participantStatuses().containsKey("engine#1"),
            Duration.ofSeconds(1));

    updater.join();

    assertThat(policySnapshot.effectivePolicyHash()).isEqualTo("active-9");
    assertThat(statuses).containsKey("engine#1");
    assertThat(event.getOccurredAtMs()).isEqualTo(9L);
    assertThat(postureSnapshot.mismatchReasons())
        .extracting(mismatch -> mismatch.mismatchReason().getCode())
        .containsExactly("ENGINE_SYNC_PENDING");
  }

  private static NamespaceSecurityPolicyDTO requestedPolicy(long version) {
    return NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.SECURED)
        .activationState(SecurityActivationState.REQUESTED)
        .desiredPolicyVersion(version)
        .desiredPolicyHash("desired-" + version)
        .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
        .requiredAuthorization(RequiredAuthorizationDTO.builder().startCommands(true).build())
        .build();
  }

  private static NamespaceSecurityPolicyDTO activePolicy(long version) {
    return activePolicy(version, SecurityMode.SECURED);
  }

  private static NamespaceSecurityPolicyDTO activePolicy(long version, SecurityMode mode) {
    return NamespaceSecurityPolicyDTO.builder()
        .mode(mode)
        .activationState(SecurityActivationState.ACTIVE)
        .desiredPolicyVersion(version)
        .desiredPolicyHash("desired-" + version)
        .activePolicyVersion(version)
        .activePolicyHash("active-" + version)
        .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
        .requiredAuthorization(RequiredAuthorizationDTO.builder().startCommands(true).build())
        .build();
  }

  private static ParticipantStatusDTO participantStatus(
      String participantInstanceId, Set<ParticipantCapability> capabilities) {
    return participantStatus(participantInstanceId, capabilities, List.of());
  }

  private static ParticipantStatusDTO participantStatus(
      String participantInstanceId,
      Set<ParticipantCapability> capabilities,
      List<PolicyMismatchReasonDTO> mismatchReasons) {
    return ParticipantStatusDTO.builder()
        .participantId(participantInstanceId.substring(0, participantInstanceId.indexOf('#')))
        .participantInstanceId(participantInstanceId)
        .participantKind(ParticipantKind.CLIENT)
        .componentType("test-component")
        .capabilities(capabilities)
        .namespace("tenant.default")
        .startedAt(1L)
        .lastSeenAt(System.currentTimeMillis())
        .statusExpiresAt(System.currentTimeMillis() + 60_000L)
        .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
        .effectiveState(
            mismatchReasons == null || mismatchReasons.isEmpty()
                ? ParticipantEffectiveState.READY
                : ParticipantEffectiveState.MISMATCH)
        .readyForDataPlane(mismatchReasons == null || mismatchReasons.isEmpty())
        .observedPolicyVersion(7L)
        .observedPolicyHash("active-7")
        .mismatchReasons(mismatchReasons == null ? List.of() : List.copyOf(mismatchReasons))
        .build();
  }

  private static PolicyMismatchReasonDTO mismatchReason(String code, String message) {
    return PolicyMismatchReasonDTO.builder().code(code).message(message).build();
  }

  private static SecurityEventDTO securityEvent(String code, long occurredAtMs) {
    return SecurityEventDTO.builder()
        .eventType(SecurityEventType.DATA_PLANE_BLOCKED)
        .severity(SecurityEventSeverity.WARNING)
        .occurredAtMs(occurredAtMs)
        .namespace("tenant.default")
        .participantId("tenant.default.client")
        .participantInstanceId("tenant.default.client#1")
        .activePolicyVersion(occurredAtMs)
        .activePolicyHash("active-" + occurredAtMs)
        .code(code)
        .message(code)
        .build();
  }

  private static final class TestHarness {

    private final AtomicReference<ObservedPolicySnapshot> observedPolicySnapshot =
        new AtomicReference<>(ObservedPolicySnapshot.empty());
    private final AtomicReference<Map<String, ParticipantStatusDTO>> participantStatuses =
        new AtomicReference<>(Map.of());
    private final AtomicReference<List<SecurityEventDTO>> securityEvents =
        new AtomicReference<>(List.of());
    private final List<NamespaceSecurityPolicyConsumer> policyConsumers = new ArrayList<>();
    private final List<ParticipantStatusConsumer> participantStatusConsumers = new ArrayList<>();
    private final List<SecurityEventConsumer> securityEventConsumers = new ArrayList<>();
    private final AtomicInteger initializerCalls = new AtomicInteger();
    private final SecurityObservabilityClient client =
        new SecurityObservabilityClient(
            observedPolicySnapshot::get,
            participantStatuses::get,
            securityEvents::get,
            new SecurityObservabilityClient.ConsumerRegistrars(
                policyConsumers::add, participantStatusConsumers::add, securityEventConsumers::add),
            initializerCalls::incrementAndGet,
            Duration.ofMillis(10));

    private void emitPolicySnapshot() {
      ObservedPolicySnapshot snapshot = observedPolicySnapshot.get();
      for (NamespaceSecurityPolicyConsumer consumer : policyConsumers) {
        consumer.accept(snapshot);
      }
    }

    private void emitParticipantStatuses() {
      Map<String, ParticipantStatusDTO> snapshot = participantStatuses.get();
      for (ParticipantStatusConsumer consumer : participantStatusConsumers) {
        consumer.accept(snapshot);
      }
    }

    private void emitSecurityEvent(SecurityEventDTO event) {
      for (SecurityEventConsumer consumer : securityEventConsumers) {
        consumer.accept(event);
      }
    }
  }
}

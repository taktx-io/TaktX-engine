/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.taktx.client.InstanceUpdateRecord;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.taktx.client.ObservedPolicySnapshot;
import io.taktx.client.SecurityPostureSnapshot;
import io.taktx.client.TaktXClient;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.VariablesDTO;
import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SecurityTestProfile.class)
@QuarkusTestResource(value = SecurityTestConfigResource.class, restrictToAnnotatedClass = true)
@Tag("security-integration")
class PublicClientObservabilityDogfoodIntegrationTest
    extends PublicClientDogfoodIntegrationTestSupport {

  @Test
  void anchoredPolicy_withoutTrustAnchor_isVisibleAsMismatchFailsClosedAndDoesNotImplyDlq() {
    long anchoredPolicyVersion = nextPolicyVersion();
    String namespace = newTestNamespace("dogfood-anchored-visibility");

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-anchored-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-anchored-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(namespace),
            participantDescriptor(
                "dogfood-anchored-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));
    TaktXClient runtimeClient =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-anchored-runtime",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "orders-console"));

    Queue<DlqEnvelope> dlqEntries = new ConcurrentLinkedQueue<>();
    observer
        .dlq()
        .registerDlqEntryConsumer("dogfood-anchored-dlq-" + UUID.randomUUID(), dlqEntries::add, true);

    awaitNoPolicy(observer);

    ObservedPolicySnapshot observedPolicy =
        publishPolicyAndAwaitObserved(
            publisher,
            observer,
            activeAnchoredPolicy(anchoredPolicyVersion),
            Duration.ofSeconds(30));

    SecurityEventDTO readinessMismatchEvent =
        observer
            .observability()
            .awaitSecurityEvent(
                event ->
                    event.getEventType() == SecurityEventType.READINESS_MISMATCH
                        && "READINESS_MISMATCH".equals(event.getCode())
                        && Long.valueOf(anchoredPolicyVersion).equals(event.getDesiredPolicyVersion()),
                Duration.ofSeconds(30));
    SecurityEventDTO blockedEvent =
        observer
            .observability()
            .awaitSecurityEvent(
                event ->
                    event.getEventType() == SecurityEventType.DATA_PLANE_BLOCKED
                        && "TRUST_ANCHOR_MISSING".equals(event.getCode())
                        && Long.valueOf(anchoredPolicyVersion).equals(event.getActivePolicyVersion()),
                Duration.ofSeconds(30));

    SecurityPostureSnapshot posture =
        observer
            .observability()
            .awaitPostureSnapshot(
                snapshot ->
                    snapshot.hasEffectivePolicy()
                        && snapshot.effectiveMode() == SecurityMode.ANCHORED_SECURED
                        && Long.valueOf(anchoredPolicyVersion).equals(snapshot.effectivePolicyVersion())
                        && snapshot.recentSecurityEvents().stream()
                            .anyMatch(
                                event ->
                                    event.getEventType() == SecurityEventType.DATA_PLANE_BLOCKED
                                        && "TRUST_ANCHOR_MISSING".equals(event.getCode())),
                Duration.ofSeconds(30));

    assertThat(observedPolicy.hasAuthoritativePolicy()).isTrue();
    assertThat(observedPolicy.effectiveMode()).isEqualTo(SecurityMode.ANCHORED_SECURED);
    assertThat(posture.currentActivationState()).isEqualTo(SecurityActivationState.ACTIVE);
    assertThat(readinessMismatchEvent.getCode()).isEqualTo("READINESS_MISMATCH");
    assertThat(blockedEvent.getCode()).isEqualTo("TRUST_ANCHOR_MISSING");
    assertThat(blockedEvent.getMessage()).contains("platform public key");
    assertThat(posture.recentSecurityEvents())
        .anyMatch(
            event ->
                event.getEventType() == SecurityEventType.READINESS_MISMATCH
                    && "READINESS_MISMATCH".equals(event.getCode()))
        .anyMatch(
            event ->
                event.getEventType() == SecurityEventType.DATA_PLANE_BLOCKED
                    && "TRUST_ANCHOR_MISSING".equals(event.getCode()));
    assertThat(posture.participantStatuses()).isEmpty();
    assertThat(posture.mismatchReasons()).isEmpty();

    assertThatThrownBy(() -> runtimeClient.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("platform public key");

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(dlqEntries).isEmpty());
  }

  @Test
  void anchoredPolicy_withoutTrustAnchor_exposesParticipantMismatchStatuses_whenEngineClockAligned()
      throws Exception {
    withEngineClockAlignedNearWallClock(
        () -> {
          long anchoredPolicyVersion = nextPolicyVersion();
          String namespace = newTestNamespace("dogfood-anchored-status");

          TaktXClient observer =
              startClient(
                  baseProperties(namespace),
                  participantDescriptor(
                      "dogfood-anchored-status-observer",
                      Set.of(ParticipantCapability.SECURITY_OBSERVER),
                      "dogfood-anchored-status-observer"));
          TaktXClient publisher =
              startClient(
                  platformWriterProperties(namespace),
                  participantDescriptor(
                      "dogfood-anchored-status-console",
                      Set.of(
                          ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                          ParticipantCapability.SECURITY_OBSERVER),
                      "console"));

          awaitNoPolicy(observer);

          ObservedPolicySnapshot observedPolicy =
              publishPolicyAndAwaitObserved(
                  publisher,
                  observer,
                  activeAnchoredPolicy(anchoredPolicyVersion),
                  Duration.ofSeconds(30));

          Map<String, ParticipantStatusDTO> participantStatuses =
              observer
                  .observability()
                  .awaitParticipantStatusSnapshot(
                      snapshot ->
                          snapshot.values().stream()
                              .anyMatch(
                                  PublicClientDogfoodIntegrationTestSupport::
                                      isAnchoredEngineMismatchStatus),
                      Duration.ofSeconds(30));

          SecurityPostureSnapshot posture =
              observer
                  .observability()
                  .awaitPostureSnapshot(
                      snapshot ->
                          snapshot.hasParticipantStatuses()
                              && snapshot.participantStatuses().values().stream()
                                  .anyMatch(
                                      PublicClientDogfoodIntegrationTestSupport::
                                          isAnchoredEngineMismatchStatus)
                              && snapshot.hasMismatchReasons(),
                      Duration.ofSeconds(30));

          assertThat(observedPolicy.effectiveMode()).isEqualTo(SecurityMode.ANCHORED_SECURED);
          assertThat(participantStatuses.values())
              .anySatisfy(
                  status -> {
                    assertThat(status.getParticipantKind()).isEqualTo(ParticipantKind.ENGINE);
                    assertThat(status.getComponentType()).isEqualTo("engine");
                    assertThat(status.getNamespace()).isEqualTo(namespace);
                    assertThat(status.getEffectiveState())
                        .isEqualTo(ParticipantEffectiveState.MISMATCH);
                    assertThat(status.isReadyForDataPlane()).isFalse();
                    assertThat(status.getObservedPolicyVersion()).isEqualTo(anchoredPolicyVersion);
                    assertThat(status.getMismatchReasons())
                        .anyMatch(reason -> "TRUST_ANCHOR_MISSING".equals(reason.getCode()));
                  });
          assertThat(posture.mismatchReasons())
              .anyMatch(
                  mismatch ->
                      "TRUST_ANCHOR_MISSING".equals(mismatch.mismatchReason().getCode()));
        });
  }

  @Test
  void securityObservability_isNamespaceScoped() {
    long securedPolicyVersion = nextPolicyVersion();
    String defaultNamespace = newTestNamespace("dogfood-default-observer");
    String isolatedNamespace = newTestNamespace("dogfood-isolated-observer");

    TaktXClient defaultObserver =
        startClient(
            baseProperties(defaultNamespace),
            participantDescriptor(
                "dogfood-default-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-default-observer"));
    TaktXClient isolatedObserver =
        startClient(
            baseProperties(isolatedNamespace),
            participantDescriptor(
                "dogfood-isolated-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-isolated-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(defaultNamespace),
            participantDescriptor(
                "dogfood-default-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));

    awaitNoPolicy(defaultObserver);
    assertThat(isolatedObserver.observability().getObservedPolicySnapshot())
        .isEqualTo(ObservedPolicySnapshot.empty());

    publishPolicyAndAwaitObserved(
        publisher,
        defaultObserver,
        activeSecuredPolicy(securedPolicyVersion),
        Duration.ofSeconds(30));

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(isolatedObserver.observability().getObservedPolicySnapshot())
                  .isEqualTo(ObservedPolicySnapshot.empty());
              assertThat(isolatedObserver.observability().getPostureSnapshot().hasEffectivePolicy())
                  .isFalse();
              assertThat(isolatedObserver.observability().getRecentSecurityEvents())
                  .noneMatch(
                      event ->
                          Long.valueOf(securedPolicyVersion).equals(event.getDesiredPolicyVersion())
                              || Long.valueOf(securedPolicyVersion)
                                  .equals(event.getActivePolicyVersion()));
            });
  }

  @Test
  void sameLogicalActor_runtimeBehaviorRemainsNamespaceScopedAcrossSecuredAndOpenNamespaces()
      throws Exception {
    long securedPolicyVersion = nextPolicyVersion();
    String securedNamespace = newTestNamespace("dogfood-cross-secured");
    String openNamespace = newTestNamespace("dogfood-cross-open");
    String sharedParticipantId = "dogfood-cross-namespace-actor";
    Set<ParticipantCapability> sharedCapabilities =
        Set.of(
            ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
            ParticipantCapability.SECURITY_OBSERVER);

    TaktXClient securedPublisher =
        startClient(
            platformWriterProperties(securedNamespace),
            participantDescriptor(
                "dogfood-cross-namespace-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));
    TaktXClient securedActor =
        startClientWithoutSigningIdentity(
            baseProperties(securedNamespace),
            participantDescriptor(sharedParticipantId, sharedCapabilities, "shared-runtime"));
    TaktXClient openActor =
        startClientWithoutSigningIdentity(
            baseProperties(openNamespace),
            participantDescriptor(sharedParticipantId, sharedCapabilities, "shared-runtime"));

    awaitNoPolicy(securedActor);
    awaitNoPolicy(openActor);

    Queue<InstanceUpdateRecord> openUpdates = new ConcurrentLinkedQueue<>();
    openActor
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-cross-namespace-open-updates-" + UUID.randomUUID(), openUpdates::addAll);

    deployProcessAndAwaitAvailability(openActor, TASK_SINGLE_BPMN, OPEN_PROCESS_ID);

    ObservedPolicySnapshot securedObservedPolicy =
        publishPolicyAndAwaitObserved(
            securedPublisher,
            securedActor,
            activeSecuredPolicy(securedPolicyVersion),
            Duration.ofSeconds(30));

    assertThat(securedObservedPolicy.effectiveMode()).isEqualTo(SecurityMode.COMMUNITY_SECURED);

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(openActor.observability().getObservedPolicySnapshot())
                  .isEqualTo(ObservedPolicySnapshot.empty());
              assertThat(openActor.observability().getPostureSnapshot().hasEffectivePolicy())
                  .isFalse();
            });

    assertThatThrownBy(() -> securedActor.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("signed client commands");

    UUID openInstanceId = openActor.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty());
    awaitProcessCompleted(openUpdates, openInstanceId);
  }
}


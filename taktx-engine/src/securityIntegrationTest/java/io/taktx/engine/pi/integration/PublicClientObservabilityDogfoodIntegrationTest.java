/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.SecurityPostureSnapshot;
import io.taktx.client.TaktXClient;
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.VariablesDTO;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(PublicClientDogfoodOpenTestProfile.class)
@QuarkusTestResource(
    value = PublicClientDogfoodOpenTestConfigResource.class,
    restrictToAnnotatedClass = true)
@Tag("security-integration")
class PublicClientObservabilityDogfoodIntegrationTest
    extends PublicClientDogfoodIntegrationTestSupport {

  @Test
  void openModeProtectedRuntimeWarning_isVisibleWithoutImplyingAnchoredMode() {
    String namespace = newTestNamespace("dogfood-open-warning-visibility");

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-open-warning-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-open-warning-observer"));
    startClient(
        baseProperties(namespace),
        participantDescriptor(
            "dogfood-open-warning-runtime",
            Set.of(
                ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                ParticipantCapability.SECURITY_OBSERVER),
            "orders-console"));

    awaitNoPolicy(observer);

    SecurityPostureSnapshot posture =
        observer
            .observability()
            .awaitPostureSnapshot(
                snapshot ->
                    snapshot.participantStatuses().values().stream()
                        .anyMatch(
                            status ->
                                "dogfood-open-warning-runtime".equals(status.getParticipantId())),
                Duration.ofSeconds(30));

    ParticipantStatusDTO runtimeStatus =
        posture.participantStatuses().values().stream()
            .filter(status -> "dogfood-open-warning-runtime".equals(status.getParticipantId()))
            .findFirst()
            .orElseThrow();

    assertThat(posture.effectiveMode()).isNotEqualTo(SecurityMode.ANCHORED);
    assertThat(runtimeStatus.getEffectiveState()).isEqualTo(ParticipantEffectiveState.READY);
    assertThat(runtimeStatus.isReadyForDataPlane()).isTrue();
    assertThat(runtimeStatus.getSupportedModes())
        .containsExactlyInAnyOrder(SecurityMode.OPEN, SecurityMode.ANCHORED);
    assertThat(runtimeStatus.getMismatchReasons())
        .anySatisfy(
            reason -> {
              assertThat(reason.getCode()).isEqualTo("ENGINE_SIGNING_UNAVAILABLE");
              assertThat(reason.getMetadata()).containsEntry("severity", "WARNING");
            });
    assertThat(posture.mismatchReasons())
        .anyMatch(
            mismatch ->
                runtimeStatus.getParticipantInstanceId().equals(mismatch.participantInstanceId())
                    && "ENGINE_SIGNING_UNAVAILABLE".equals(mismatch.mismatchReason().getCode()));
  }

  @Test
  void publishingAnchoredPolicy_doesNotChangeStartupStaticOpenPostureOrBlockRuntime()
      throws Exception {
    String namespace = newTestNamespace("dogfood-policy-static-open");

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-policy-static-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-policy-static-observer"));
    startClient(
        platformWriterProperties(namespace),
        participantDescriptor(
            "dogfood-policy-static-console",
            Set.of(ParticipantCapability.SECURITY_OBSERVER),
            "console"));
    TaktXClient runtimeClient =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-policy-static-runtime",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "orders-console"));

    awaitNoPolicy(observer);

    java.util.Queue<InstanceUpdateRecord> updates = new ConcurrentLinkedQueue<>();
    runtimeClient
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-policy-static-updates-" + UUID.randomUUID(), updates::addAll);
    deployProcessAndAwaitAvailability(runtimeClient, TASK_SINGLE_BPMN, OPEN_PROCESS_ID);

    NamespaceSecurityPolicyDTO publishedPolicy = activeAnchoredPolicy();
    TaktXClient.publishNamespaceSecurityPolicy(
        platformWriterProperties(namespace), publishedPolicy);

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              SecurityPostureSnapshot posture = observer.observability().getPostureSnapshot();
              assertThat(posture.effectiveMode()).isNotEqualTo(SecurityMode.ANCHORED);
              assertThat(posture.recentSecurityEvents())
                  .noneMatch(event -> event.getEventType() == SecurityEventType.DATA_PLANE_BLOCKED);
            });

    UUID instanceId = runtimeClient.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty());
    awaitProcessCompleted(updates, instanceId);

    assertThat(publishedPolicy.getMode()).isEqualTo(SecurityMode.ANCHORED);
  }

  @Test
  void securityObservability_isNamespaceScoped_whenPolicyIsPublishedElsewhere() {
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
    startClient(
        platformWriterProperties(defaultNamespace),
        participantDescriptor(
            "dogfood-default-console", Set.of(ParticipantCapability.SECURITY_OBSERVER), "console"));

    awaitNoPolicy(defaultObserver);
    awaitNoPolicy(isolatedObserver);

    TaktXClient.publishNamespaceSecurityPolicy(
        platformWriterProperties(defaultNamespace), activeAnchoredPolicy());

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(isolatedObserver.observability().getPostureSnapshot().effectiveMode())
                  .isNotEqualTo(SecurityMode.ANCHORED);
              assertThat(isolatedObserver.observability().getRecentSecurityEvents()).isEmpty();
            });
  }

  @Test
  void sameLogicalActor_participantStatusRemainsNamespaceScopedAcrossNamespaces() throws Exception {
    String defaultNamespace = newTestNamespace("dogfood-cross-default");
    String isolatedNamespace = newTestNamespace("dogfood-cross-isolated");
    String sharedParticipantId = "dogfood-cross-namespace-actor";
    Set<ParticipantCapability> sharedCapabilities =
        Set.of(
            ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
            ParticipantCapability.SECURITY_OBSERVER);

    TaktXClient defaultObserver =
        startClient(
            baseProperties(defaultNamespace),
            participantDescriptor(
                "dogfood-cross-default-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-cross-default-observer"));
    TaktXClient isolatedObserver =
        startClient(
            baseProperties(isolatedNamespace),
            participantDescriptor(
                "dogfood-cross-isolated-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-cross-isolated-observer"));
    TaktXClient defaultActor =
        startClient(
            baseProperties(defaultNamespace),
            participantDescriptor(sharedParticipantId, sharedCapabilities, "shared-runtime"));
    startClientWithoutSigningIdentity(
        baseProperties(isolatedNamespace),
        participantDescriptor(sharedParticipantId, sharedCapabilities, "shared-runtime"));

    awaitNoPolicy(defaultObserver);
    awaitNoPolicy(isolatedObserver);

    Map<String, ParticipantStatusDTO> defaultStatuses =
        defaultObserver
            .observability()
            .awaitParticipantStatusSnapshot(
                snapshot ->
                    snapshot.values().stream()
                        .anyMatch(status -> sharedParticipantId.equals(status.getParticipantId())),
                Duration.ofSeconds(30));

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(isolatedObserver.observability().getParticipantStatusSnapshot().values())
                  .noneMatch(status -> sharedParticipantId.equals(status.getParticipantId()));
              assertThat(isolatedObserver.observability().getPostureSnapshot().effectiveMode())
                  .isNotEqualTo(SecurityMode.ANCHORED);
            });

    java.util.Queue<InstanceUpdateRecord> updates = new ConcurrentLinkedQueue<>();
    defaultActor
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-cross-default-updates-" + UUID.randomUUID(), updates::addAll);

    deployProcessAndAwaitAvailability(defaultActor, TASK_SINGLE_BPMN, OPEN_PROCESS_ID);

    UUID instanceId = defaultActor.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty());
    awaitProcessCompleted(updates, instanceId);

    assertThat(defaultStatuses.values())
        .anySatisfy(
            status -> {
              assertThat(status.getNamespace()).isEqualTo(defaultNamespace);
              assertThat(status.getParticipantId()).isEqualTo(sharedParticipantId);
              assertThat(status.getEffectiveState()).isEqualTo(ParticipantEffectiveState.READY);
            });
  }
}

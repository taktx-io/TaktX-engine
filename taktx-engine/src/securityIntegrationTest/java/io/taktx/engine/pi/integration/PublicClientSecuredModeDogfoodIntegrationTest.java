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

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.ObservedPolicySnapshot;
import io.taktx.client.SecurityPostureSnapshot;
import io.taktx.client.TaktXClient;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.VariablesDTO;
import java.time.Duration;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SecurityTestProfile.class)
@QuarkusTestResource(value = SecurityTestConfigResource.class, restrictToAnnotatedClass = true)
@Tag("security-integration")
class PublicClientSecuredModeDogfoodIntegrationTest
    extends PublicClientDogfoodIntegrationTestSupport {

  @Test
  void
      securedNamespace_rejectsRoguePolicyMutation_blocksUnauthorizedStart_and_allowsAuthorizedRuntimeAndSignedWorkerCompletion()
          throws Exception {
    long securedPolicyVersion = nextPolicyVersion();
    String namespace = newTestNamespace("dogfood-secured-runtime");

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(namespace),
            participantDescriptor(
                "dogfood-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));
    TaktXClient runtimeClient =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-runtime",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "orders-console"));
    TaktXClient workerClient =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-worker",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "worker-service"));

    awaitNoPolicy(observer);

    Queue<InstanceUpdateRecord> updates = new ConcurrentLinkedQueue<>();
    runtimeClient
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-secured-updates-" + UUID.randomUUID(), updates::addAll);
    deployProcessAndAwaitAvailability(runtimeClient, SERVICE_TASK_BPMN, SERVICE_PROCESS_ID);

    Queue<ExternalTaskTriggerDTO> triggers = new ConcurrentLinkedQueue<>();
    String externalTaskTopic = workerClient.workers().requestExternalTaskTopic(SERVICE_TASK_TYPE);
    awaitTopicExists(externalTaskTopic);
    workerClient
        .workers()
        .registerExternalTaskConsumer(
            collectingExternalTaskConsumer(triggers), "dogfood-worker-" + UUID.randomUUID());

    AtomicReference<SecurityEventDTO> rejectionEvent = new AtomicReference<>();
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .ignoreExceptions()
        .until(
            () -> {
              TaktXClient.publishNamespaceSecurityPolicy(
                  rogueWriterProperties(namespace),
                  requestedSecuredPolicy(securedPolicyVersion - 1));
              SecurityEventDTO matchingEvent =
                  observer.observability().getRecentSecurityEvents().stream()
                      .filter(
                          event ->
                              event.getEventType()
                                      == SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED
                                  && event.getMessage() != null
                                  && event.getMessage().contains(ROGUE_WRITER_KEY_ID)
                                  && event.getMessage().contains("required role PLATFORM"))
                      .findFirst()
                      .orElse(null);
              if (matchingEvent != null) {
                rejectionEvent.set(matchingEvent);
                return true;
              }
              return false;
            });
    assertThat(rejectionEvent.get()).isNotNull();
    assertThat(rejectionEvent.get().getMessage()).contains("required role PLATFORM");
    awaitNoPolicy(observer);

    ObservedPolicySnapshot observedPolicy =
        publishPolicyAndAwaitObserved(
            publisher, observer, activeSecuredPolicy(securedPolicyVersion), Duration.ofSeconds(30));
    awaitObservedPolicyVersion(runtimeClient, securedPolicyVersion);
    awaitObservedPolicyVersion(workerClient, securedPolicyVersion);

    SecurityPostureSnapshot posture =
        observer
            .observability()
            .awaitPostureSnapshot(
                snapshot ->
                    snapshot.hasEffectivePolicy()
                        && snapshot.recentSecurityEvents().stream()
                            .anyMatch(
                                event ->
                                    event.getEventType()
                                            == SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED
                                        && event.getMessage() != null
                                        && event.getMessage().contains(ROGUE_WRITER_KEY_ID)),
                Duration.ofSeconds(30));

    assertThat(observedPolicy.effectiveMode()).isEqualTo(SecurityMode.COMMUNITY_SECURED);
    assertThat(posture.recentSecurityEvents())
        .extracting(SecurityEventDTO::getEventType)
        .contains(SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED);

    assertThatThrownBy(
            () -> runtimeClient.runtime().startProcess(SERVICE_PROCESS_ID, VariablesDTO.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT authorization for start commands");

    UUID instanceId =
        runtimeClient
            .runtime()
            .startProcess(
                SERVICE_PROCESS_ID, -1, VariablesDTO.empty(), jwt("START", SERVICE_PROCESS_ID, -1));

    ExternalTaskTriggerDTO trigger = awaitExternalTaskTrigger(triggers, instanceId);
    workerClient
        .runtime()
        .completeExternalTask(
            trigger.getProcessInstanceId(),
            trigger.getElementInstanceIdPath(),
            VariablesDTO.empty());

    awaitProcessCompleted(updates, instanceId);
  }

  @Test
  void signingRequiredStart_acceptsSignedClientWithoutJwt() throws Exception {
    long securedPolicyVersion = nextPolicyVersion();
    String namespace = newTestNamespace("dogfood-signing-required");

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-signing-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-signing-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(namespace),
            participantDescriptor(
                "dogfood-signing-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));
    TaktXClient signedRuntimeClient =
        startClient(
            signedRuntimeProperties(namespace),
            participantDescriptor(
                "dogfood-signed-runtime",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "orders-console"));

    awaitNoPolicy(observer);

    Queue<InstanceUpdateRecord> updates = new ConcurrentLinkedQueue<>();
    signedRuntimeClient
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-signing-updates-" + UUID.randomUUID(), updates::addAll);

    deployProcessAndAwaitAvailability(signedRuntimeClient, TASK_SINGLE_BPMN, OPEN_PROCESS_ID);

    publishPolicyAndAwaitObserved(
        publisher,
        observer,
        activeSigningRequiredPolicy(securedPolicyVersion),
        Duration.ofSeconds(30));
    awaitObservedPolicyVersion(signedRuntimeClient, securedPolicyVersion);

    UUID instanceId =
        signedRuntimeClient.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty());

    awaitProcessCompleted(updates, instanceId);
  }

  @Test
  void securedWorker_withoutSigningIdentity_cannotConsumeProtectedWork_andLeavesProcessIncomplete()
      throws Exception {
    long securedPolicyVersion = nextPolicyVersion();
    String namespace = newTestNamespace("dogfood-secured-worker-negative");
    String unsignedWorkerParticipantId = "dogfood-unsigned-worker";

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-worker-negative-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-worker-negative-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(namespace),
            participantDescriptor(
                "dogfood-worker-negative-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));
    TaktXClient runtimeClient =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-worker-negative-runtime",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "orders-console"));
    TaktXClient unsignedWorkerClient =
        startClientWithoutSigningIdentity(
            baseProperties(namespace),
            participantDescriptor(
                unsignedWorkerParticipantId,
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "worker-service"));

    awaitNoPolicy(observer);

    Queue<InstanceUpdateRecord> updates = new ConcurrentLinkedQueue<>();
    runtimeClient
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-worker-negative-updates-" + UUID.randomUUID(), updates::addAll);
    deployProcessAndAwaitAvailability(runtimeClient, SERVICE_TASK_BPMN, SERVICE_PROCESS_ID);

    Queue<ExternalTaskTriggerDTO> triggers = new ConcurrentLinkedQueue<>();
    String externalTaskTopic =
        unsignedWorkerClient.workers().requestExternalTaskTopic(SERVICE_TASK_TYPE);
    awaitTopicExists(externalTaskTopic);
    unsignedWorkerClient
        .workers()
        .registerExternalTaskConsumer(
            collectingExternalTaskConsumer(triggers),
            "dogfood-worker-negative-" + UUID.randomUUID());

    ObservedPolicySnapshot observedPolicy =
        publishPolicyAndAwaitObserved(
            publisher, observer, activeSecuredPolicy(securedPolicyVersion), Duration.ofSeconds(30));
    awaitObservedPolicyVersion(runtimeClient, securedPolicyVersion);
    awaitObservedPolicyVersion(unsignedWorkerClient, securedPolicyVersion);

    SecurityPostureSnapshot posture =
        observer
            .observability()
            .awaitPostureSnapshot(
                snapshot ->
                    snapshot.hasEffectivePolicy()
                        && snapshot.effectiveMode() == SecurityMode.COMMUNITY_SECURED
                        && Long.valueOf(securedPolicyVersion)
                            .equals(snapshot.effectivePolicyVersion()),
                Duration.ofSeconds(30));

    assertThat(observedPolicy.effectiveMode()).isEqualTo(SecurityMode.COMMUNITY_SECURED);
    assertThat(posture.currentActivationState()).isEqualTo(SecurityActivationState.ACTIVE);

    UUID instanceId =
        runtimeClient
            .runtime()
            .startProcess(
                SERVICE_PROCESS_ID, -1, VariablesDTO.empty(), jwt("START", SERVICE_PROCESS_ID, -1));

    SecurityEventDTO readinessMismatchEvent =
        observer
            .observability()
            .awaitSecurityEvent(
                event ->
                    event.getEventType() == SecurityEventType.READINESS_MISMATCH
                        && "READINESS_MISMATCH".equals(event.getCode())
                        && Long.valueOf(securedPolicyVersion)
                            .equals(event.getDesiredPolicyVersion()),
                Duration.ofSeconds(30));

    SecurityPostureSnapshot blockedWorkerPosture =
        observer
            .observability()
            .awaitPostureSnapshot(
                snapshot ->
                    snapshot.hasRecentSecurityEvents()
                        && snapshot.recentSecurityEvents().stream()
                            .anyMatch(
                                event ->
                                    event.getEventType() == SecurityEventType.READINESS_MISMATCH
                                        && "READINESS_MISMATCH".equals(event.getCode())
                                        && Long.valueOf(securedPolicyVersion)
                                            .equals(event.getDesiredPolicyVersion())),
                Duration.ofSeconds(30));

    assertThat(readinessMismatchEvent.getCode()).isEqualTo("READINESS_MISMATCH");
    assertThat(blockedWorkerPosture.recentSecurityEvents())
        .anyMatch(
            event ->
                event.getEventType() == SecurityEventType.READINESS_MISMATCH
                    && "READINESS_MISMATCH".equals(event.getCode())
                    && Long.valueOf(securedPolicyVersion).equals(event.getDesiredPolicyVersion()));

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(triggers)
                    .noneMatch(trigger -> instanceId.equals(trigger.getProcessInstanceId())));

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(latestProcessState(updates, instanceId))
                    .isEqualTo(ExecutionState.ACTIVE));
  }
}

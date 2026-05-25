/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.ObservedPolicySnapshot;
import io.taktx.client.SecurityPostureSnapshot;
import io.taktx.client.TaktXClient;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.VariablesDTO;
import java.time.Duration;
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
class PublicClientOpenModeDogfoodIntegrationTest extends PublicClientDogfoodIntegrationTestSupport {

  @Test
  void communityOpenNamespace_allowsPublicClientRuntimeWithoutSecurityBootstrap() throws Exception {
    String namespace = newTestNamespace("dogfood-open-runtime");

    TaktXClient client =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-open-client",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-open-client"));

    awaitNoPolicy(client);

    Queue<InstanceUpdateRecord> updates = new ConcurrentLinkedQueue<>();
    client
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-open-updates-" + UUID.randomUUID(), updates::addAll);

    deployProcessAndAwaitAvailability(client, TASK_SINGLE_BPMN, OPEN_PROCESS_ID);

    UUID instanceId = client.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty());
    awaitProcessCompleted(updates, instanceId);

    ObservedPolicySnapshot observedPolicy = client.observability().getObservedPolicySnapshot();
    SecurityPostureSnapshot posture = client.observability().getPostureSnapshot();
    assertThat(observedPolicy).isEqualTo(ObservedPolicySnapshot.empty());
    assertThat(posture.hasEffectivePolicy()).isFalse();
  }

  @Test
  void communityOpenPolicy_isPublishedObservedAndCanBeCleared() {
    long openPolicyVersion = nextPolicyVersion();
    String namespace = newTestNamespace("dogfood-open-policy");

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-open-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-open-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(namespace),
            participantDescriptor(
                "dogfood-open-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));

    awaitNoPolicy(observer);

    ObservedPolicySnapshot observedPolicy =
        publishPolicyAndAwaitObserved(
            publisher,
            observer,
            activeCommunityOpenPolicy(openPolicyVersion),
            Duration.ofSeconds(30));

    SecurityPostureSnapshot posture =
        observer
            .observability()
            .awaitPostureSnapshot(
                snapshot ->
                    snapshot.hasEffectivePolicy()
                        && snapshot.effectiveMode() == SecurityMode.COMMUNITY_OPEN
                        && Long.valueOf(openPolicyVersion).equals(snapshot.effectivePolicyVersion()),
                Duration.ofSeconds(30));

    assertThat(observedPolicy.hasAuthoritativePolicy()).isTrue();
    assertThat(observedPolicy.effectiveMode()).isEqualTo(SecurityMode.COMMUNITY_OPEN);
    assertThat(observedPolicy.effectivePolicyVersion()).isEqualTo(openPolicyVersion);
    assertThat(posture.currentActivationState()).isEqualTo(SecurityActivationState.ACTIVE);

    publisher.security().clearNamespaceSecurityPolicy();
    awaitNoPolicy(observer);
    assertThat(observer.observability().getPostureSnapshot().hasEffectivePolicy()).isFalse();
  }

  @Test
  void communityOpenPolicy_allowsUnsignedWorkerCompletionThroughPublicClient() throws Exception {
    long openPolicyVersion = nextPolicyVersion();
    String namespace = newTestNamespace("dogfood-open-worker");

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-open-worker-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-open-worker-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(namespace),
            participantDescriptor(
                "dogfood-open-worker-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));
    TaktXClient runtimeClient =
        startClientWithoutSigningIdentity(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-open-worker-runtime",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "orders-console"));
    TaktXClient workerClient =
        startClientWithoutSigningIdentity(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-open-worker",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "worker-service"));

    awaitNoPolicy(observer);

    Queue<InstanceUpdateRecord> updates = new ConcurrentLinkedQueue<>();
    runtimeClient
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-open-worker-updates-" + UUID.randomUUID(), updates::addAll);
    deployProcessAndAwaitAvailability(runtimeClient, SERVICE_TASK_BPMN, SERVICE_PROCESS_ID);

    Queue<ExternalTaskTriggerDTO> triggers = new ConcurrentLinkedQueue<>();
    String externalTaskTopic = workerClient.workers().requestExternalTaskTopic(SERVICE_TASK_TYPE);
    awaitTopicExists(externalTaskTopic);
    workerClient
        .workers()
        .registerExternalTaskConsumer(
            collectingExternalTaskConsumer(triggers),
            "dogfood-open-worker-" + UUID.randomUUID());

    ObservedPolicySnapshot observedPolicy =
        publishPolicyAndAwaitObserved(
            publisher,
            observer,
            activeCommunityOpenPolicy(openPolicyVersion),
            Duration.ofSeconds(30));
    awaitObservedPolicyVersion(runtimeClient, openPolicyVersion);
    awaitObservedPolicyVersion(workerClient, openPolicyVersion);

    SecurityPostureSnapshot posture =
        observer
            .observability()
            .awaitPostureSnapshot(
                snapshot ->
                    snapshot.hasEffectivePolicy()
                        && snapshot.effectiveMode() == SecurityMode.COMMUNITY_OPEN
                        && Long.valueOf(openPolicyVersion).equals(snapshot.effectivePolicyVersion()),
                Duration.ofSeconds(30));

    assertThat(observedPolicy.effectiveMode()).isEqualTo(SecurityMode.COMMUNITY_OPEN);
    assertThat(posture.currentActivationState()).isEqualTo(SecurityActivationState.ACTIVE);

    UUID instanceId = runtimeClient.runtime().startProcess(SERVICE_PROCESS_ID, VariablesDTO.empty());

    ExternalTaskTriggerDTO trigger = awaitExternalTaskTrigger(triggers, instanceId);
    workerClient
        .runtime()
        .completeExternalTask(
            trigger.getProcessInstanceId(), trigger.getElementInstanceIdPath(), VariablesDTO.empty());

    awaitProcessCompleted(updates, instanceId);
  }
}


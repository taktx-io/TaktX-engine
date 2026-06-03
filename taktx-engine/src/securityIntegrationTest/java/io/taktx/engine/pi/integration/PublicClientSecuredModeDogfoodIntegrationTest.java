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
import io.taktx.client.ObservedPolicySnapshot;
import io.taktx.client.SecurityPostureSnapshot;
import io.taktx.client.TaktXClient;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.VariablesDTO;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
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
  void anchoredNamespace_rejectsRoguePolicyMutation_and_failsClosed_untilAnchoredTrustIsConfigured()
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
    primeAuthoritativePolicyMutationPath(publisher, observer, Duration.ofSeconds(30));

    deployProcessAndAwaitAvailability(runtimeClient, SERVICE_TASK_BPMN, SERVICE_PROCESS_ID);

    java.util.Queue<ExternalTaskTriggerDTO> triggers =
        new java.util.concurrent.ConcurrentLinkedQueue<>();
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

    assertThat(observedPolicy.effectiveMode()).isEqualTo(SecurityMode.ANCHORED);
    assertThat(posture.recentSecurityEvents())
        .extracting(SecurityEventDTO::getEventType)
        .contains(SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED);

    assertThatThrownBy(
            () -> runtimeClient.runtime().startProcess(SERVICE_PROCESS_ID, VariablesDTO.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("platform public key");

    assertThatThrownBy(
            () ->
                runtimeClient
                    .runtime()
                    .startProcess(
                        SERVICE_PROCESS_ID,
                        -1,
                        VariablesDTO.empty(),
                        jwt("START", SERVICE_PROCESS_ID, -1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("platform public key");
  }

  @Test
  void anchoredNamespace_rejectsSignedClientWithoutJwt_untilAnchoredTrustIsConfigured()
      throws Exception {
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

    deployProcessAndAwaitAvailability(signedRuntimeClient, TASK_SINGLE_BPMN, OPEN_PROCESS_ID);

    publishPolicyAndAwaitObserved(
        publisher,
        observer,
        activeSigningRequiredPolicy(securedPolicyVersion),
        Duration.ofSeconds(30));
    awaitObservedPolicyVersion(signedRuntimeClient, securedPolicyVersion);

    assertThatThrownBy(
            () -> signedRuntimeClient.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("platform public key");
  }

  @Test
  void anchoredPolicy_withoutTrustAnchor_preventsProtectedWorkFromStarting() throws Exception {
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

    deployProcessAndAwaitAvailability(runtimeClient, SERVICE_TASK_BPMN, SERVICE_PROCESS_ID);

    java.util.Queue<ExternalTaskTriggerDTO> triggers =
        new java.util.concurrent.ConcurrentLinkedQueue<>();
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
                        && snapshot.effectiveMode() == SecurityMode.ANCHORED
                        && Long.valueOf(securedPolicyVersion)
                            .equals(snapshot.effectivePolicyVersion()),
                Duration.ofSeconds(30));

    assertThat(observedPolicy.effectiveMode()).isEqualTo(SecurityMode.ANCHORED);
    assertThat(posture.currentActivationState()).isNull();

    assertThatThrownBy(
            () ->
                runtimeClient
                    .runtime()
                    .startProcess(
                        SERVICE_PROCESS_ID,
                        -1,
                        VariablesDTO.empty(),
                        jwt("START", SERVICE_PROCESS_ID, -1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("platform public key");

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(triggers).isEmpty());
  }

  @Test
  void anchoredNamespace_acceptsSignedClientWithApprovedIdentity() throws Exception {
    long securedPolicyVersion = nextPolicyVersion();
    String namespace = newTestNamespace("dogfood-anchored-positive");

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-anchored-pos-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-anchored-pos-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(namespace),
            participantDescriptor(
                "dogfood-anchored-pos-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));

    // Runtime client: signs automatically; RUNTIME_SIGNER_KEY_ID is pre-published in the trust
    // registry by publishTrustedControlPlaneKeysForNamespace, and the platform trust anchor is
    // configured so the client-side participation guard permits ANCHORED operation.
    TaktXClient runtimeClient =
        startClient(
            signedAnchoredRuntimeProperties(namespace),
            participantDescriptor(
                "dogfood-anchored-pos-runtime",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "anchored-client"));

    awaitNoPolicy(observer);

    java.util.Queue<io.taktx.client.InstanceUpdateRecord> updates =
        new java.util.concurrent.ConcurrentLinkedQueue<>();
    runtimeClient
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-anchored-pos-updates-" + UUID.randomUUID(), updates::addAll);

    deployProcessAndAwaitAvailability(runtimeClient, TASK_SINGLE_BPMN, OPEN_PROCESS_ID);

    publishPolicyAndAwaitObserved(
        publisher, observer, activeAnchoredPolicy(securedPolicyVersion), Duration.ofSeconds(30));
    awaitObservedPolicyVersion(runtimeClient, securedPolicyVersion);

    // The client auto-signs; the engine verifies the signature against the pre-published key
    // and accepts the request — demonstrating the full ANCHORED happy path.
    UUID instanceId = runtimeClient.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty());
    awaitProcessCompleted(updates, instanceId);
  }

  @Test
  void anchoredNamespace_rejectsSignedClientWithUnpublishedKey() throws Exception {
    long securedPolicyVersion = nextPolicyVersion();
    String namespace = newTestNamespace("dogfood-anchored-unknown-key");

    TaktXClient observer =
        startClient(
            baseProperties(namespace),
            participantDescriptor(
                "dogfood-unknown-key-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-unknown-key-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(namespace),
            participantDescriptor(
                "dogfood-unknown-key-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));

    // Generate a fresh keypair whose public key is never published to taktx-signing-keys.
    // Omitting the public-key property causes TaktXClient to skip auto-publication while still
    // signing outbound messages — the engine receives a validly-signed request but cannot
    // resolve the key ID and must reject it.
    java.security.KeyPair unknownKeys = io.taktx.security.SigningKeyGenerator.generate();
    String unknownPrivateKey = io.taktx.security.SigningKeyGenerator.encodePrivateKey(unknownKeys.getPrivate());

    TaktXClient unknownKeyClient =
        startClient(
            signingOnlyWithoutPublishedKeyProperties(namespace, "unknown-key-dogfood", unknownPrivateKey),
            participantDescriptor(
                "dogfood-unknown-key-runtime",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "unknown-key-client"));

    awaitNoPolicy(observer);

    java.util.Queue<io.taktx.client.InstanceUpdateRecord> updates =
        new java.util.concurrent.ConcurrentLinkedQueue<>();
    unknownKeyClient
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-unknown-key-updates-" + UUID.randomUUID(), updates::addAll);

    deployProcessAndAwaitAvailability(unknownKeyClient, TASK_SINGLE_BPMN, OPEN_PROCESS_ID);

    publishPolicyAndAwaitObserved(
        publisher, observer, activeAnchoredPolicy(securedPolicyVersion), Duration.ofSeconds(30));
    awaitObservedPolicyVersion(unknownKeyClient, securedPolicyVersion);

    // The engine should reject the signed start command because "unknown-key-dogfood" is not
    // present in the taktx-signing-keys KTable — no process instance should be created.
    unknownKeyClient.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty());

    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(6))
        .untilAsserted(() -> assertThat(updates).isEmpty());
  }
}

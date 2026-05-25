/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.security.NamespaceSecurityPolicyActivationService;
import io.taktx.engine.security.SecurityEventPublisher;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.serdes.NamespaceSecurityPolicyProtoMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class NamespaceSecurityPolicyProcessorTest {

  private static final String POLICY_TOPIC = "default.taktx-security-policy";
  private static final String STORE_NAME = "namespace-security-policy-store";

  private TopologyTestDriver driver;
  private TestInputTopic<String, byte[]> policyTopic;
  private NamespaceSecurityPolicyStore namespaceSecurityPolicyStore;

  @BeforeEach
  void setUp() {
    namespaceSecurityPolicyStore = new NamespaceSecurityPolicyStore();

    StreamsBuilder builder = new StreamsBuilder();
    builder.addGlobalStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.ByteArray())
            .withLoggingDisabled(),
        POLICY_TOPIC,
        Consumed.with(Serdes.String(), Serdes.ByteArray()),
        () -> new NamespaceSecurityPolicyProcessor(namespaceSecurityPolicyStore));

    Topology topology = builder.build();

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "namespace-security-policy-test");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

    driver = new TopologyTestDriver(topology, config);
    policyTopic =
        driver.createInputTopic(
            POLICY_TOPIC, Serdes.String().serializer(), Serdes.ByteArray().serializer());
  }

  @AfterEach
  void tearDown() {
    driver.close();
  }

  @Test
  void policyKey_updatesStoreWithValidatedNormalizedPolicy() {
    NamespaceSecurityPolicyDTO input =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(42L)
            .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
            .requiredAuthorization(RequiredAuthorizationDTO.builder().startCommands(true).build())
            .activePolicyVersion(42L)
            .build();

    policyTopic.pipeInput(
        NamespaceSecurityPolicyProcessor.POLICY_KEY,
        NamespaceSecurityPolicyProtoMapper.toProto(input).toByteArray());

    NamespaceSecurityPolicyDTO stored = namespaceSecurityPolicyStore.get();
    assertThat(stored).isNotNull();
    assertThat(stored.getDesiredPolicyVersion()).isEqualTo(42L);
    assertThat(stored.getDesiredPolicyHash()).isNotBlank();
    assertThat(stored.getActivePolicyHash()).isEqualTo(stored.getDesiredPolicyHash());
  }

  @Test
  void tombstone_clearsStore() {
    NamespaceSecurityPolicyDTO input =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_OPEN)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(1L)
            .build();

    policyTopic.pipeInput(
        NamespaceSecurityPolicyProcessor.POLICY_KEY,
        NamespaceSecurityPolicyProtoMapper.toProto(input).toByteArray());
    assertThat(namespaceSecurityPolicyStore.get()).isNotNull();

    policyTopic.pipeInput(NamespaceSecurityPolicyProcessor.POLICY_KEY, null);

    assertThat(namespaceSecurityPolicyStore.get()).isNull();
  }

  @Test
  void invalidPolicy_doesNotReplacePreviousStoreValue() {
    NamespaceSecurityPolicyDTO valid =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(7L)
            .build();
    policyTopic.pipeInput(
        NamespaceSecurityPolicyProcessor.POLICY_KEY,
        NamespaceSecurityPolicyProtoMapper.toProto(valid).toByteArray());

    NamespaceSecurityPolicyDTO previous = namespaceSecurityPolicyStore.get();

    NamespaceSecurityPolicyDTO invalid =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(8L)
            .build();

    policyTopic.pipeInput(
        NamespaceSecurityPolicyProcessor.POLICY_KEY,
        NamespaceSecurityPolicyProtoMapper.toProto(invalid).toByteArray());

    assertThat(namespaceSecurityPolicyStore.get()).isEqualTo(previous);
  }

  @Test
  void invalidActivePolicyWithoutActiveIdentity_doesNotReplacePreviousStoreValue() {
    NamespaceSecurityPolicyDTO valid =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(7L)
            .build();
    policyTopic.pipeInput(
        NamespaceSecurityPolicyProcessor.POLICY_KEY,
        NamespaceSecurityPolicyProtoMapper.toProto(valid).toByteArray());

    NamespaceSecurityPolicyDTO previous = namespaceSecurityPolicyStore.get();

    NamespaceSecurityPolicyDTO invalid =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(8L)
            .desiredPolicyHash("requested-hash")
            .build();

    policyTopic.pipeInput(
        NamespaceSecurityPolicyProcessor.POLICY_KEY,
        NamespaceSecurityPolicyProtoMapper.toProto(invalid).toByteArray());

    assertThat(namespaceSecurityPolicyStore.get()).isEqualTo(previous);
  }

  @Test
  void conflictingDesiredAndLegacyAliases_failClosed() {
    NamespaceSecurityPolicyDTO valid =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(7L)
            .build();
    policyTopic.pipeInput(
        NamespaceSecurityPolicyProcessor.POLICY_KEY,
        NamespaceSecurityPolicyProtoMapper.toProto(valid).toByteArray());

    NamespaceSecurityPolicyDTO previous = namespaceSecurityPolicyStore.get();

    NamespaceSecurityPolicyDTO invalid =
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_SECURED)
            .activationState(SecurityActivationState.REQUESTED)
            .desiredPolicyVersion(9L)
            .policyVersion(10L)
            .build();

    policyTopic.pipeInput(
        NamespaceSecurityPolicyProcessor.POLICY_KEY,
        NamespaceSecurityPolicyProtoMapper.toProto(invalid).toByteArray());

    assertThat(namespaceSecurityPolicyStore.get()).isEqualTo(previous);
  }

  @Test
  void nonPolicyKey_isIgnored() {
    policyTopic.pipeInput("other", new byte[] {1, 2, 3});

    assertThat(namespaceSecurityPolicyStore.get()).isNull();
  }

  @Test
  void requestedPolicy_entersValidatingWhenLifecycleSupportIsEnabled() {
    NamespaceSecurityPolicyStore lifecycleStore = new NamespaceSecurityPolicyStore();
    ParticipantStatusStore participantStatusStore = new ParticipantStatusStore();
    TaktConfiguration configuration = Mockito.mock(TaktConfiguration.class);
    Mockito.when(configuration.getSecurityPolicyActivationTimeoutMs()).thenReturn(30_000L);
    Mockito.when(configuration.getTenantId()).thenReturn("tenant");
    Mockito.when(configuration.getNamespace()).thenReturn("bank.payments");
    Mockito.when(configuration.getHost()).thenReturn("engine-host");
    Mockito.when(configuration.getPort()).thenReturn(8080);
    NamespaceSecurityPolicyActivationService activationService =
        new NamespaceSecurityPolicyActivationService(
            configuration,
            lifecycleStore,
            participantStatusStore,
            Mockito.mock(SecurityEventPublisher.class),
            Clock.fixed(Instant.ofEpochMilli(1_716_450_000_000L), ZoneOffset.UTC));

    StreamsBuilder builder = new StreamsBuilder();
    builder.addGlobalStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.ByteArray())
            .withLoggingDisabled(),
        POLICY_TOPIC,
        Consumed.with(Serdes.String(), Serdes.ByteArray()),
        () -> new NamespaceSecurityPolicyProcessor(lifecycleStore, activationService));

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "namespace-security-policy-lifecycle-test");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

    try (TopologyTestDriver lifecycleDriver = new TopologyTestDriver(builder.build(), config)) {
      TestInputTopic<String, byte[]> lifecycleTopic =
          lifecycleDriver.createInputTopic(
              POLICY_TOPIC, Serdes.String().serializer(), Serdes.ByteArray().serializer());
      NamespaceSecurityPolicyDTO input =
          NamespaceSecurityPolicyDTO.builder()
              .mode(SecurityMode.COMMUNITY_SECURED)
              .activationState(SecurityActivationState.REQUESTED)
              .desiredPolicyVersion(52L)
              .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
              .requiredAuthorization(RequiredAuthorizationDTO.builder().startCommands(true).build())
              .build();

      lifecycleTopic.pipeInput(
          NamespaceSecurityPolicyProcessor.POLICY_KEY,
          NamespaceSecurityPolicyProtoMapper.toProto(input).toByteArray());

      assertThat(lifecycleStore.get()).isNotNull();
      assertThat(lifecycleStore.get().getActivationState())
          .isEqualTo(SecurityActivationState.VALIDATING);
      assertThat(lifecycleStore.getValidationStartedAtMs()).isEqualTo(1_716_450_000_000L);
    }
  }

  @Test
  void invalidPolicy_emitsControlPlaneMutationRejectedEventWhenLifecycleSupportIsEnabled() {
    NamespaceSecurityPolicyStore lifecycleStore = new NamespaceSecurityPolicyStore();
    ParticipantStatusStore participantStatusStore = new ParticipantStatusStore();
    TaktConfiguration configuration = Mockito.mock(TaktConfiguration.class);
    SecurityEventPublisher securityEventPublisher = Mockito.mock(SecurityEventPublisher.class);
    Mockito.when(configuration.getSecurityPolicyActivationTimeoutMs()).thenReturn(30_000L);
    Mockito.when(configuration.getTenantId()).thenReturn("tenant");
    Mockito.when(configuration.getNamespace()).thenReturn("bank.payments");
    Mockito.when(configuration.getHost()).thenReturn("engine-host");
    Mockito.when(configuration.getPort()).thenReturn(8080);
    NamespaceSecurityPolicyActivationService activationService =
        new NamespaceSecurityPolicyActivationService(
            configuration,
            lifecycleStore,
            participantStatusStore,
            securityEventPublisher,
            Clock.fixed(Instant.ofEpochMilli(1_716_450_000_000L), ZoneOffset.UTC));

    StreamsBuilder builder = new StreamsBuilder();
    builder.addGlobalStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.ByteArray())
            .withLoggingDisabled(),
        POLICY_TOPIC,
        Consumed.with(Serdes.String(), Serdes.ByteArray()),
        () -> new NamespaceSecurityPolicyProcessor(lifecycleStore, activationService));

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "namespace-security-policy-rejection-test");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

    try (TopologyTestDriver lifecycleDriver = new TopologyTestDriver(builder.build(), config)) {
      TestInputTopic<String, byte[]> lifecycleTopic =
          lifecycleDriver.createInputTopic(
              POLICY_TOPIC, Serdes.String().serializer(), Serdes.ByteArray().serializer());
      NamespaceSecurityPolicyDTO invalid =
          NamespaceSecurityPolicyDTO.builder()
              .mode(SecurityMode.ANCHORED_SECURED)
              .activationState(SecurityActivationState.REQUESTED)
              .desiredPolicyVersion(52L)
              .build();

      lifecycleTopic.pipeInput(
          NamespaceSecurityPolicyProcessor.POLICY_KEY,
          NamespaceSecurityPolicyProtoMapper.toProto(invalid).toByteArray());

      assertThat(lifecycleStore.get()).isNull();
      ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
      Mockito.verify(securityEventPublisher).publish(captor.capture());
      assertThat(captor.getValue().getEventType())
          .isEqualTo(SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED);
      assertThat(captor.getValue().getCode())
          .isEqualTo(NamespaceSecurityPolicyActivationService.INVALID_POLICY_MUTATION_CODE);
      assertThat(captor.getValue().getMetadata()).containsEntry("recordKey", "policy");
    }
  }

  @Test
  void newRequestedPolicyDuringValidation_replacesDesiredIdentityAndRemainsValidating() {
    NamespaceSecurityPolicyStore lifecycleStore = new NamespaceSecurityPolicyStore();
    ParticipantStatusStore participantStatusStore = new ParticipantStatusStore();
    TaktConfiguration configuration = Mockito.mock(TaktConfiguration.class);
    Mockito.when(configuration.getSecurityPolicyActivationTimeoutMs()).thenReturn(30_000L);
    Mockito.when(configuration.getTenantId()).thenReturn("tenant");
    Mockito.when(configuration.getNamespace()).thenReturn("bank.payments");
    Mockito.when(configuration.getHost()).thenReturn("engine-host");
    Mockito.when(configuration.getPort()).thenReturn(8080);
    NamespaceSecurityPolicyActivationService activationService =
        new NamespaceSecurityPolicyActivationService(
            configuration,
            lifecycleStore,
            participantStatusStore,
            Mockito.mock(SecurityEventPublisher.class),
            Clock.fixed(Instant.ofEpochMilli(1_716_450_000_000L), ZoneOffset.UTC));

    StreamsBuilder builder = new StreamsBuilder();
    builder.addGlobalStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.ByteArray())
            .withLoggingDisabled(),
        POLICY_TOPIC,
        Consumed.with(Serdes.String(), Serdes.ByteArray()),
        () -> new NamespaceSecurityPolicyProcessor(lifecycleStore, activationService));

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "namespace-security-policy-revalidation-test");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

    try (TopologyTestDriver lifecycleDriver = new TopologyTestDriver(builder.build(), config)) {
      TestInputTopic<String, byte[]> lifecycleTopic =
          lifecycleDriver.createInputTopic(
              POLICY_TOPIC, Serdes.String().serializer(), Serdes.ByteArray().serializer());
      NamespaceSecurityPolicyDTO firstRequested =
          NamespaceSecurityPolicyDTO.builder()
              .mode(SecurityMode.COMMUNITY_SECURED)
              .activationState(SecurityActivationState.REQUESTED)
              .desiredPolicyVersion(52L)
              .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
              .requiredAuthorization(RequiredAuthorizationDTO.builder().startCommands(true).build())
              .build();
      NamespaceSecurityPolicyDTO replacementRequested =
          NamespaceSecurityPolicyDTO.builder()
              .mode(SecurityMode.COMMUNITY_SECURED)
              .activationState(SecurityActivationState.REQUESTED)
              .desiredPolicyVersion(53L)
              .requiredSigning(RequiredSigningDTO.builder().workerResponses(true).build())
              .requiredAuthorization(
                  RequiredAuthorizationDTO.builder().userTaskCompletion(true).build())
              .build();

      lifecycleTopic.pipeInput(
          NamespaceSecurityPolicyProcessor.POLICY_KEY,
          NamespaceSecurityPolicyProtoMapper.toProto(firstRequested).toByteArray());
      assertThat(lifecycleStore.get()).isNotNull();
      assertThat(lifecycleStore.get().getActivationState())
          .isEqualTo(SecurityActivationState.VALIDATING);

      lifecycleTopic.pipeInput(
          NamespaceSecurityPolicyProcessor.POLICY_KEY,
          NamespaceSecurityPolicyProtoMapper.toProto(replacementRequested).toByteArray());

      assertThat(lifecycleStore.get()).isNotNull();
      assertThat(lifecycleStore.get().getActivationState())
          .isEqualTo(SecurityActivationState.VALIDATING);
      assertThat(lifecycleStore.get().getDesiredPolicyVersion()).isEqualTo(53L);
      assertThat(lifecycleStore.get().getRequiredSigning().isWorkerResponses()).isTrue();
      assertThat(lifecycleStore.get().getRequiredAuthorization().isUserTaskCompletion()).isTrue();
    }
  }

  @Test
  void unauthorizedPolicyMutation_doesNotReplacePreviousStoreValueAndEmitsRejectedEvent() {
    NamespaceSecurityPolicyStore lifecycleStore = new NamespaceSecurityPolicyStore();
    ParticipantStatusStore participantStatusStore = new ParticipantStatusStore();
    TaktConfiguration configuration = Mockito.mock(TaktConfiguration.class);
    SecurityEventPublisher securityEventPublisher = Mockito.mock(SecurityEventPublisher.class);
    EngineAuthorizationService authorizationService =
        Mockito.mock(EngineAuthorizationService.class);
    Mockito.when(configuration.getSecurityPolicyActivationTimeoutMs()).thenReturn(30_000L);
    Mockito.when(configuration.getTenantId()).thenReturn("tenant");
    Mockito.when(configuration.getNamespace()).thenReturn("bank.payments");
    Mockito.when(configuration.getHost()).thenReturn("engine-host");
    Mockito.when(configuration.getPort()).thenReturn(8080);
    NamespaceSecurityPolicyActivationService activationService =
        new NamespaceSecurityPolicyActivationService(
            configuration,
            lifecycleStore,
            participantStatusStore,
            securityEventPublisher,
            Clock.fixed(Instant.ofEpochMilli(1_716_450_000_000L), ZoneOffset.UTC));

    lifecycleStore.update(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_OPEN)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(7L)
            .activePolicyVersion(7L)
            .build());

    StreamsBuilder builder = new StreamsBuilder();
    builder.addGlobalStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.ByteArray())
            .withLoggingDisabled(),
        POLICY_TOPIC,
        Consumed.with(Serdes.String(), Serdes.ByteArray()),
        () ->
            new NamespaceSecurityPolicyProcessor(
                lifecycleStore, activationService, authorizationService));

    Properties config = new Properties();
    config.put(
        StreamsConfig.APPLICATION_ID_CONFIG, "namespace-security-policy-authz-rejection-test");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

    try (TopologyTestDriver lifecycleDriver = new TopologyTestDriver(builder.build(), config)) {
      TestInputTopic<String, byte[]> lifecycleTopic =
          lifecycleDriver.createInputTopic(
              POLICY_TOPIC, Serdes.String().serializer(), Serdes.ByteArray().serializer());
      NamespaceSecurityPolicyDTO requested =
          NamespaceSecurityPolicyDTO.builder()
              .mode(SecurityMode.COMMUNITY_SECURED)
              .activationState(SecurityActivationState.REQUESTED)
              .desiredPolicyVersion(52L)
              .requiredSigning(RequiredSigningDTO.builder().engineOutbound(true).build())
              .build();
      byte[] payload = NamespaceSecurityPolicyProtoMapper.toProto(requested).toByteArray();

      Mockito.doThrow(
              new AuthorizationTokenException(
                  "Signing keyId 'console-key' is not trusted for required role PLATFORM"))
          .when(authorizationService)
          .authorizeNamespaceSecurityPolicyMutation(Mockito.any(), Mockito.eq(payload));

      lifecycleTopic.pipeInput(
          new TestRecord<>(
              NamespaceSecurityPolicyProcessor.POLICY_KEY,
              payload,
              new RecordHeaders(),
              Instant.ofEpochMilli(1_716_450_000_100L)));

      assertThat(lifecycleStore.get().getDesiredPolicyVersion()).isEqualTo(7L);
      ArgumentCaptor<SecurityEventDTO> captor = ArgumentCaptor.forClass(SecurityEventDTO.class);
      Mockito.verify(securityEventPublisher).publish(captor.capture());
      assertThat(captor.getValue().getEventType())
          .isEqualTo(SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED);
      assertThat(captor.getValue().getMetadata()).containsEntry("recordKey", "policy");
    }
  }

  @Test
  void authorizedTombstone_clearsStoreWhenMutationAuthorizerAllowsIt() {
    NamespaceSecurityPolicyStore lifecycleStore = new NamespaceSecurityPolicyStore();
    EngineAuthorizationService authorizationService =
        Mockito.mock(EngineAuthorizationService.class);
    lifecycleStore.update(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.COMMUNITY_OPEN)
            .activationState(SecurityActivationState.ACTIVE)
            .desiredPolicyVersion(7L)
            .activePolicyVersion(7L)
            .build());

    StreamsBuilder builder = new StreamsBuilder();
    builder.addGlobalStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.ByteArray())
            .withLoggingDisabled(),
        POLICY_TOPIC,
        Consumed.with(Serdes.String(), Serdes.ByteArray()),
        () -> new NamespaceSecurityPolicyProcessor(lifecycleStore, null, authorizationService));

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "namespace-security-policy-authz-clear-test");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

    try (TopologyTestDriver lifecycleDriver = new TopologyTestDriver(builder.build(), config)) {
      TestInputTopic<String, byte[]> lifecycleTopic =
          lifecycleDriver.createInputTopic(
              POLICY_TOPIC, Serdes.String().serializer(), Serdes.ByteArray().serializer());

      lifecycleTopic.pipeInput(
          new TestRecord<>(
              NamespaceSecurityPolicyProcessor.POLICY_KEY,
              null,
              new RecordHeaders(),
              Instant.ofEpochMilli(1_716_450_000_200L)));

      assertThat(lifecycleStore.get()).isNull();
      Mockito.verify(authorizationService)
          .authorizeNamespaceSecurityPolicyMutation(Mockito.any(), Mockito.isNull());
    }
  }
}

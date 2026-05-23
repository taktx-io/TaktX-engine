/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantRole;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.engine.security.NamespaceSecurityPolicyActivationService;
import io.taktx.serdes.ParticipantStatusProtoMapper;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ParticipantStatusProcessorTest {

  private static final String STATUS_TOPIC = "default.taktx-participant-status";
  private static final String STORE_NAME = "participant-status-store";

  private TopologyTestDriver driver;
  private TestInputTopic<String, byte[]> statusTopic;
  private ParticipantStatusStore participantStatusStore;

  @BeforeEach
  void setUp() {
    participantStatusStore = new ParticipantStatusStore();

    StreamsBuilder builder = new StreamsBuilder();
    builder.addGlobalStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.ByteArray())
            .withLoggingDisabled(),
        STATUS_TOPIC,
        Consumed.with(Serdes.String(), Serdes.ByteArray()),
        () -> new ParticipantStatusProcessor(participantStatusStore));

    Topology topology = builder.build();

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "participant-status-test");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

    driver = new TopologyTestDriver(topology, config);
    statusTopic =
        driver.createInputTopic(
            STATUS_TOPIC, Serdes.String().serializer(), Serdes.ByteArray().serializer());
  }

  @AfterEach
  void tearDown() {
    driver.close();
  }

  @Test
  void statusRecord_updatesStore() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("engine-2")
            .participantInstanceId("engine-2-pod-7f8c4d")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(1716450000000L)
            .lastSeenAt(1716450060000L)
            .statusExpiresAt(1716450120000L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .observedPolicyVersion(42L)
            .observedPolicyHash("abc123")
            .mismatchReasons(
                List.of(
                    PolicyMismatchReasonDTO.builder()
                        .code("TRUST_ANCHOR_MISSING")
                        .message(
                            "Namespace requires anchored trust but no platform public key is configured")
                        .build()))
            .build();

    statusTopic.pipeInput(
        status.getParticipantInstanceId(),
        ParticipantStatusProtoMapper.toProto(status).toByteArray());

    assertThat(participantStatusStore.get(status.getParticipantInstanceId())).isEqualTo(status);
    assertThat(participantStatusStore.currentSnapshot(1716450119999L)).hasSize(1);
  }

  @Test
  void tombstone_removesStatusFromStore() {
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("engine-2")
            .participantInstanceId("engine-2-pod-7f8c4d")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(1716450000000L)
            .lastSeenAt(1716450060000L)
            .statusExpiresAt(1716450120000L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.STALE)
            .build();

    statusTopic.pipeInput(
        status.getParticipantInstanceId(),
        ParticipantStatusProtoMapper.toProto(status).toByteArray());
    assertThat(participantStatusStore.snapshot()).hasSize(1);

    statusTopic.pipeInput(status.getParticipantInstanceId(), null);

    assertThat(participantStatusStore.snapshot()).isEmpty();
  }

  @Test
  void invalidStatus_doesNotReplacePreviousValue() {
    ParticipantStatusDTO valid =
        ParticipantStatusDTO.builder()
            .participantId("engine-2")
            .participantInstanceId("engine-2-pod-7f8c4d")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(1716450000000L)
            .lastSeenAt(1716450060000L)
            .statusExpiresAt(1716450120000L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(42L)
            .observedPolicyHash("abc123")
            .build();

    statusTopic.pipeInput(
        valid.getParticipantInstanceId(),
        ParticipantStatusProtoMapper.toProto(valid).toByteArray());

    ParticipantStatusDTO previous = participantStatusStore.get(valid.getParticipantInstanceId());

    ParticipantStatusDTO invalid =
        ParticipantStatusDTO.builder()
            .participantId("engine-2")
            .participantInstanceId("engine-2-pod-7f8c4d")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(1716450000000L)
            .lastSeenAt(1716450060000L)
            .statusExpiresAt(1716450120000L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(true)
            .build();

    statusTopic.pipeInput(
        invalid.getParticipantInstanceId(),
        ParticipantStatusProtoMapper.toProto(invalid).toByteArray());

    assertThat(participantStatusStore.get(valid.getParticipantInstanceId())).isEqualTo(previous);
  }

  @Test
  void currentSnapshot_excludesExpiredStatuses() {
    ParticipantStatusDTO expired =
        ParticipantStatusDTO.builder()
            .participantId("engine-2")
            .participantInstanceId("engine-2-expired")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(1716450000000L)
            .lastSeenAt(1716450060000L)
            .statusExpiresAt(1716450060001L)
            .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .observedPolicyVersion(42L)
            .observedPolicyHash("abc123")
            .build();
    ParticipantStatusDTO current =
        expired.toBuilder()
            .participantInstanceId("engine-2-current")
            .statusExpiresAt(1716450120000L)
            .build();

    statusTopic.pipeInput(
        expired.getParticipantInstanceId(), ParticipantStatusProtoMapper.toProto(expired).toByteArray());
    statusTopic.pipeInput(
        current.getParticipantInstanceId(), ParticipantStatusProtoMapper.toProto(current).toByteArray());

    assertThat(participantStatusStore.currentSnapshot(1716450119999L))
        .containsOnlyKeys(current.getParticipantInstanceId());
  }

  @Test
  void blankKey_isIgnored() {
    statusTopic.pipeInput("", new byte[] {1, 2, 3});

    assertThat(participantStatusStore.snapshot()).isEmpty();
  }

  @Test
  void statusRecord_withLifecycleSupport_updatesStoreAndTriggersActivationReevaluation() {
    ParticipantStatusStore lifecycleStore = new ParticipantStatusStore();
    NamespaceSecurityPolicyActivationService activationService =
        Mockito.mock(NamespaceSecurityPolicyActivationService.class);

    StreamsBuilder builder = new StreamsBuilder();
    builder.addGlobalStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.ByteArray())
            .withLoggingDisabled(),
        STATUS_TOPIC,
        Consumed.with(Serdes.String(), Serdes.ByteArray()),
        () -> new ParticipantStatusProcessor(lifecycleStore, activationService));

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "participant-status-lifecycle-test");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

    try (TopologyTestDriver lifecycleDriver = new TopologyTestDriver(builder.build(), config)) {
      TestInputTopic<String, byte[]> lifecycleTopic =
          lifecycleDriver.createInputTopic(
              STATUS_TOPIC, Serdes.String().serializer(), Serdes.ByteArray().serializer());
      ParticipantStatusDTO status =
          ParticipantStatusDTO.builder()
              .participantId("tenant.bank.payments.console")
              .participantInstanceId("console-1")
              .role(ParticipantRole.CONSOLE)
              .namespace("bank.payments")
              .startedAt(1716450000000L)
              .lastSeenAt(1716450060000L)
              .statusExpiresAt(1716450120000L)
              .statusVerificationLevel(StatusVerificationLevel.UNVERIFIED_STATUS)
              .effectiveState(ParticipantEffectiveState.MISMATCH)
              .readyForDataPlane(false)
              .observedPolicyVersion(42L)
              .observedPolicyHash("abc123")
              .mismatchReasons(
                  List.of(
                      PolicyMismatchReasonDTO.builder()
                          .code("POLICY_NOT_ACTIVE")
                          .message("policy still converging")
                          .build()))
              .build();

      lifecycleTopic.pipeInput(
          status.getParticipantInstanceId(), ParticipantStatusProtoMapper.toProto(status).toByteArray());

      assertThat(lifecycleStore.get(status.getParticipantInstanceId())).isEqualTo(status);
      Mockito.verify(activationService).onParticipantStatusesChanged();
    }
  }
}

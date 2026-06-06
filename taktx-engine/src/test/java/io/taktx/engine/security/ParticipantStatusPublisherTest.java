/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.serdes.ParticipantStatusProtoMapper;
import java.util.Set;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ParticipantStatusPublisherTest {

  private static final Set<ParticipantCapability> ENGINE_CAPABILITIES =
      Set.of(ParticipantCapability.ENFORCER, ParticipantCapability.SECURITY_OBSERVER);

  private TaktConfiguration configuration;
  private EngineSecurityReadinessEvaluator readinessEvaluator;
  private SecurityEventPublisher securityEventPublisher;
  private KafkaProducer<String, byte[]> producer;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    configuration = Mockito.mock(TaktConfiguration.class);
    when(configuration.getPrefixed("taktx-participant-status"))
        .thenReturn("tenant.bank.payments.taktx-participant-status");
    readinessEvaluator = Mockito.mock(EngineSecurityReadinessEvaluator.class);
    securityEventPublisher = Mockito.mock(SecurityEventPublisher.class);
    producer = mock(KafkaProducer.class);
    when(producer.send(any()))
        .thenReturn(
            java.util.concurrent.CompletableFuture.completedFuture(
                new RecordMetadata(
                    new TopicPartition("tenant.bank.payments.taktx-participant-status", 0),
                    0,
                    0,
                    0,
                    0,
                    0)));
  }

  @Test
  void toRecord_usesParticipantInstanceIdAsKey() throws Exception {
    ParticipantStatusDTO status = readyEngineStatus();

    ProducerRecord<String, byte[]> producerRecord =
        ParticipantStatusPublisher.toRecord(
            "tenant.bank.payments.taktx-participant-status", status);

    assertThat(producerRecord.key()).isEqualTo(status.getParticipantInstanceId());
    assertThat(
            ParticipantStatusProtoMapper.toDto(
                io.taktx.proto.ParticipantStatusMessage.parseFrom(producerRecord.value())))
        .isEqualTo(status);
  }

  @Test
  void publishCurrentStatus_evaluatesAndPublishesStatus() {
    ParticipantStatusDTO status = readyEngineStatus();
    when(readinessEvaluator.evaluateCurrentStatus()).thenReturn(status);

    ParticipantStatusPublisher publisher =
        new ParticipantStatusPublisher(
            configuration,
            readinessEvaluator,
            securityEventPublisher,
            producer);

    ParticipantStatusDTO published = publisher.publishCurrentStatus();

    assertThat(published).isEqualTo(status);
    verify(producer).send(any());
    verify(producer).flush();
  }

  @Test
  void publish_rejectsNullStatus() {
    ParticipantStatusPublisher publisher =
        new ParticipantStatusPublisher(
            configuration,
            readinessEvaluator,
            securityEventPublisher,
            producer);

    assertThatThrownBy(() -> publisher.publish(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("status must not be null");
  }

  @Test
  void publishCurrentStatus_emitsDataPlaneBlockedEventForBlockedAnchoredPolicy() {
    ParticipantStatusDTO status =
        readyEngineStatus().toBuilder()
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .mismatchReasons(
                java.util.List.of(
                    PolicyMismatchReasonDTO.builder()
                        .code("TRUST_ANCHOR_MISSING")
                        .message("trust anchor missing")
                        .build()))
            .build();
    when(readinessEvaluator.evaluateCurrentStatus()).thenReturn(status);

    ParticipantStatusPublisher publisher =
        new ParticipantStatusPublisher(
            configuration,
            readinessEvaluator,
            securityEventPublisher,
            producer);

    publisher.publishCurrentStatus();

    ArgumentCaptor<SecurityEventDTO> eventCaptor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher).publish(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventType())
        .isEqualTo(SecurityEventType.DATA_PLANE_BLOCKED);
    assertThat(eventCaptor.getValue().getCode()).isEqualTo("TRUST_ANCHOR_MISSING");
  }

  @Test
  void publishCurrentStatus_deduplicatesRepeatedBlockedEventsUntilStateChanges() {
    ParticipantStatusDTO blocked =
        readyEngineStatus().toBuilder()
            .effectiveState(ParticipantEffectiveState.MISMATCH)
            .readyForDataPlane(false)
            .mismatchReasons(
                java.util.List.of(
                    PolicyMismatchReasonDTO.builder()
                        .code("TRUST_ANCHOR_MISSING")
                        .message("trust anchor missing")
                        .build()))
            .build();
    ParticipantStatusDTO recovered =
        blocked.toBuilder()
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .mismatchReasons(java.util.List.of())
            .build();
    when(readinessEvaluator.evaluateCurrentStatus())
        .thenReturn(blocked, blocked, recovered, blocked);

    ParticipantStatusPublisher publisher =
        new ParticipantStatusPublisher(
            configuration,
            readinessEvaluator,
            securityEventPublisher,
            producer);

    publisher.publishCurrentStatus();
    publisher.publishCurrentStatus();
    publisher.publishCurrentStatus();
    publisher.publishCurrentStatus();

    ArgumentCaptor<SecurityEventDTO> eventCaptor = ArgumentCaptor.forClass(SecurityEventDTO.class);
    verify(securityEventPublisher, Mockito.times(2)).publish(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues())
        .extracting(SecurityEventDTO::getEventType)
        .containsOnly(SecurityEventType.DATA_PLANE_BLOCKED);
    assertThat(eventCaptor.getAllValues().getFirst().getCode()).isEqualTo("TRUST_ANCHOR_MISSING");
    verify(producer, Mockito.times(4)).send(any());
  }

  private static ParticipantStatusDTO readyEngineStatus() {
    return ParticipantStatusDTO.builder()
        .participantId("tenant.bank.payments.engine")
        .participantInstanceId("tenant.bank.payments@engine-host:8080#123")
        .participantKind(ParticipantKind.ENGINE)
        .componentType("engine")
        .capabilities(ENGINE_CAPABILITIES)
        .namespace("bank.payments")
        .startedAt(100L)
        .lastSeenAt(150L)
        .statusExpiresAt(200L)
        .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
        .effectiveState(ParticipantEffectiveState.READY)
        .readyForDataPlane(true)
        .build();
  }
}

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

import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantRole;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.serdes.ParticipantStatusProtoMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ParticipantStatusPublisherTest {

  private TaktConfiguration configuration;
  private EngineSecurityReadinessEvaluator readinessEvaluator;
  private KafkaProducer<String, byte[]> producer;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    configuration = Mockito.mock(TaktConfiguration.class);
    when(configuration.getPrefixed("taktx-participant-status"))
        .thenReturn("tenant.bank.payments.taktx-participant-status");
    readinessEvaluator = Mockito.mock(EngineSecurityReadinessEvaluator.class);
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
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.engine")
            .participantInstanceId("tenant.bank.payments@engine-host:8080#123")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(150L)
            .statusExpiresAt(200L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .build();

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
    ParticipantStatusDTO status =
        ParticipantStatusDTO.builder()
            .participantId("tenant.bank.payments.engine")
            .participantInstanceId("tenant.bank.payments@engine-host:8080#123")
            .role(ParticipantRole.ENGINE)
            .namespace("bank.payments")
            .startedAt(100L)
            .lastSeenAt(150L)
            .statusExpiresAt(200L)
            .statusVerificationLevel(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
            .effectiveState(ParticipantEffectiveState.READY)
            .readyForDataPlane(true)
            .build();
    when(readinessEvaluator.evaluateCurrentStatus()).thenReturn(status);

    ParticipantStatusPublisher publisher =
        new ParticipantStatusPublisher(configuration, readinessEvaluator, producer);

    ParticipantStatusDTO published = publisher.publishCurrentStatus();

    assertThat(published).isEqualTo(status);
    verify(producer).send(any());
    verify(producer).flush();
  }

  @Test
  void publish_rejectsNullStatus() {
    ParticipantStatusPublisher publisher =
        new ParticipantStatusPublisher(configuration, readinessEvaluator, producer);

    assertThatThrownBy(() -> publisher.publish(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("status must not be null");
  }
}

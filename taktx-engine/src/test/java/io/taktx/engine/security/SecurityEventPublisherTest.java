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

import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventSeverity;
import io.taktx.dto.SecurityEventType;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.serdes.SecurityEventProtoMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SecurityEventPublisherTest {

  private TaktConfiguration configuration;
  private KafkaProducer<String, byte[]> producer;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    configuration = Mockito.mock(TaktConfiguration.class);
    Mockito.when(configuration.getPrefixed("taktx-security-events"))
        .thenReturn("tenant.bank.payments.taktx-security-events");
    producer = mock(KafkaProducer.class);
    Mockito.when(producer.send(any()))
        .thenReturn(
            java.util.concurrent.CompletableFuture.completedFuture(
                new RecordMetadata(
                    new TopicPartition("tenant.bank.payments.taktx-security-events", 0),
                    0,
                    0,
                    0,
                    0,
                    0)));
  }

  @Test
  void toRecord_usesProvidedKeyAndSerializesEvent() throws Exception {
    SecurityEventDTO event =
        SecurityEventDTO.builder()
            .eventType(SecurityEventType.ACTIVATION_TIMEOUT)
            .severity(SecurityEventSeverity.ERROR)
            .occurredAtMs(123L)
            .namespace("bank.payments")
            .code("ACTIVATION_TIMEOUT")
            .message("timeout")
            .build();

    ProducerRecord<String, byte[]> producerRecord =
        SecurityEventPublisher.toRecord(
            "tenant.bank.payments.taktx-security-events", "event-1", event);

    assertThat(producerRecord.topic()).isEqualTo("tenant.bank.payments.taktx-security-events");
    assertThat(producerRecord.key()).isEqualTo("event-1");
    assertThat(
            SecurityEventProtoMapper.toDto(
                io.taktx.proto.SecurityEventMessage.parseFrom(producerRecord.value())))
        .isEqualTo(event);
  }

  @Test
  void publish_sendsProducerRecordAndFlushes() {
    SecurityEventDTO event =
        SecurityEventDTO.builder()
            .eventType(SecurityEventType.POLICY_CHANGE)
            .severity(SecurityEventSeverity.INFO)
            .occurredAtMs(123L)
            .namespace("bank.payments")
            .code("POLICY_CHANGE")
            .message("policy changed")
            .build();

    SecurityEventPublisher publisher = new SecurityEventPublisher(configuration, producer);
    publisher.publish("event-1", event);

    verify(producer).send(any());
    verify(producer).flush();
  }

  @Test
  void publish_rejectsNullEvent() {
    SecurityEventPublisher publisher = new SecurityEventPublisher(configuration, producer);

    assertThatThrownBy(() -> publisher.publish(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("event must not be null");
  }
}

/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taktx.client.auth.AuthorizationTokenProvider;
import io.taktx.dto.Constants;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.util.TaktPropertiesHelper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessInstanceProducerTest {

  private KafkaProducer<UUID, ProcessInstanceTriggerDTO> producer;
  private TaktPropertiesHelper propertiesHelper;

  @BeforeEach
  void setUp() {
    producer = mock(KafkaProducer.class);
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.engine.tenant-id", "tenant");
    properties.setProperty("taktx.engine.namespace", "default");
    propertiesHelper = new TaktPropertiesHelper(properties);
  }

  @Test
  void startProcess_usesAuthorizationTokenProviderWhenExplicitTokenIsMissing() {
    AuthorizationTokenProvider tokenProvider = request -> "jwt-from-provider";
    ProcessInstanceProducer processInstanceProducer =
        new ProcessInstanceProducer(propertiesHelper, producer, tokenProvider);

    UUID processInstanceId =
        processInstanceProducer.startProcess("invoice", 7, VariablesDTO.empty(), null);

    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(captor.capture());
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> record = captor.getValue();

    assertThat(record.key()).isEqualTo(processInstanceId);
    assertThat(record.value()).isInstanceOf(StartCommandDTO.class);
    assertThat(headerValue(record, Constants.HEADER_AUTHORIZATION)).isEqualTo("jwt-from-provider");
  }

  @Test
  void abortElementInstance_explicitTokenOverridesAuthorizationTokenProvider() {
    AuthorizationTokenProvider tokenProvider = request -> "jwt-from-provider";
    ProcessInstanceProducer processInstanceProducer =
        new ProcessInstanceProducer(propertiesHelper, producer, tokenProvider);
    UUID processInstanceId = UUID.randomUUID();

    processInstanceProducer.abortElementInstance(
        processInstanceId, List.of(1L, 2L), "jwt-explicit");

    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(captor.capture());
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> record = captor.getValue();

    assertThat(record.key()).isEqualTo(processInstanceId);
    assertThat(headerValue(record, Constants.HEADER_AUTHORIZATION)).isEqualTo("jwt-explicit");
  }

  @Test
  void startProcess_providerReturningBlankTokenFailsFast() {
    AuthorizationTokenProvider tokenProvider = request -> "   ";
    ProcessInstanceProducer processInstanceProducer =
        new ProcessInstanceProducer(propertiesHelper, producer, tokenProvider);

    assertThatThrownBy(
            () -> processInstanceProducer.startProcess("invoice", 1, VariablesDTO.empty(), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AuthorizationTokenProvider returned no token");
  }

  @Test
  void startProcess_withoutExplicitTokenOrProvider_sendsNoAuthorizationHeader() {
    ProcessInstanceProducer processInstanceProducer =
        new ProcessInstanceProducer(propertiesHelper, producer, null);

    processInstanceProducer.startProcess("invoice", 3, VariablesDTO.empty(), null);

    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(captor.capture());
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> record = captor.getValue();

    assertThat(record.headers().lastHeader(Constants.HEADER_AUTHORIZATION)).isNull();
  }

  private String headerValue(
      ProducerRecord<UUID, ProcessInstanceTriggerDTO> record, String headerName) {
    return new String(record.headers().lastHeader(headerName).value(), StandardCharsets.UTF_8);
  }
}

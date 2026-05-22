/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taktx.client.auth.AuthorizationTokenProvider;
import io.taktx.client.auth.CommandAuthorizationScope;
import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
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

class ProcessInstanceResponderTest {

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
  void completeUserTask_explicitTokenAddsAuthorizationHeader() {
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, null);
    UUID processInstanceId = UUID.randomUUID();

    responder.completeUserTask(
        processInstanceId, List.of(10L, 20L), VariablesDTO.of("approved", true), "jwt-explicit");

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> record = capture();
    assertThat(record.key()).isEqualTo(processInstanceId);
    assertThat(record.value()).isInstanceOf(UserTaskResponseTriggerDTO.class);
    assertThat(headerValue(record)).isEqualTo("jwt-explicit");

    UserTaskResponseTriggerDTO trigger = (UserTaskResponseTriggerDTO) record.value();
    assertThat(trigger.getMessageId()).isNotBlank();
    assertThat(trigger.getElementInstanceIdPath()).containsExactly(10L, 20L);
  }

  @Test
  void completeExternalTask_usesAuthorizationTokenProviderWhenExplicitTokenMissing() {
    AuthorizationTokenProvider provider =
        request -> {
          assertThat(request.scope()).isEqualTo(CommandAuthorizationScope.EXTERNAL_TASK_COMPLETE);
          assertThat(request.elementInstanceIdPath()).containsExactly(11L, 22L);
          return "jwt-from-provider";
        };
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, provider);
    UUID processInstanceId = UUID.randomUUID();

    responder.completeExternalTask(
        processInstanceId, List.of(11L, 22L), VariablesDTO.of("status", "done"), null);

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> record = capture();
    assertThat(record.key()).isEqualTo(processInstanceId);
    assertThat(record.value()).isInstanceOf(ExternalTaskResponseTriggerDTO.class);
    assertThat(headerValue(record)).isEqualTo("jwt-from-provider");

    ExternalTaskResponseTriggerDTO trigger = (ExternalTaskResponseTriggerDTO) record.value();
    assertThat(trigger.getMessageId()).isNotBlank();
    assertThat(trigger.getElementInstanceIdPath()).containsExactly(11L, 22L);
  }

  @Test
  void completeExternalTask_withoutProviderOrToken_sendsNoAuthorizationHeader() {
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, null);

    responder.completeExternalTask(UUID.randomUUID(), List.of(1L), VariablesDTO.empty());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> record = capture();
    assertThat(record.headers().lastHeader(Constants.HEADER_AUTHORIZATION)).isNull();
  }

  @Test
  void completeUserTask_providerReturningBlankTokenFailsFast() {
    AuthorizationTokenProvider provider = request -> "   ";
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, provider);

    assertThatThrownBy(
            () ->
                responder.completeUserTask(
                    UUID.randomUUID(), List.of(1L), VariablesDTO.empty(), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AuthorizationTokenProvider returned no token");
  }

  @SuppressWarnings("unchecked")
  private ProducerRecord<UUID, ProcessInstanceTriggerDTO> capture() {
    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(captor.capture());
    return captor.getValue();
  }

  private String headerValue(ProducerRecord<UUID, ProcessInstanceTriggerDTO> record) {
    return new String(
        record.headers().lastHeader(Constants.HEADER_AUTHORIZATION).value(),
        StandardCharsets.UTF_8);
  }
}

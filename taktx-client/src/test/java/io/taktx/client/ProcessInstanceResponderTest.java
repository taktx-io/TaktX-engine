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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.taktx.client.auth.AuthorizationTokenProvider;
import io.taktx.client.auth.CommandAuthorizationScope;
import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.variables.Variables;
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

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> triggerRecord = capture();
    assertThat(triggerRecord.key()).isEqualTo(processInstanceId);
    assertThat(triggerRecord.value()).isInstanceOf(UserTaskResponseTriggerDTO.class);
    assertThat(headerValue(triggerRecord)).isEqualTo("jwt-explicit");

    UserTaskResponseTriggerDTO trigger = (UserTaskResponseTriggerDTO) triggerRecord.value();
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

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> triggerRecord = capture();
    assertThat(triggerRecord.key()).isEqualTo(processInstanceId);
    assertThat(triggerRecord.value()).isInstanceOf(ExternalTaskResponseTriggerDTO.class);
    assertThat(headerValue(triggerRecord)).isEqualTo("jwt-from-provider");

    ExternalTaskResponseTriggerDTO trigger = (ExternalTaskResponseTriggerDTO) triggerRecord.value();
    assertThat(trigger.getMessageId()).isNotBlank();
    assertThat(trigger.getElementInstanceIdPath()).containsExactly(11L, 22L);
  }

  @Test
  void completeExternalTask_withoutProviderOrToken_sendsNoAuthorizationHeader() {
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, null);

    responder.completeExternalTask(UUID.randomUUID(), List.of(1L), VariablesDTO.empty());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> triggerRecord = capture();
    assertThat(triggerRecord.headers().lastHeader(Constants.HEADER_AUTHORIZATION)).isNull();
  }

  @Test
  void errorUserTask_explicitTokenAddsAuthorizationHeaderAndErrorPayload() {
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, null);
    UUID processInstanceId = UUID.randomUUID();

    responder.errorUserTask(
        processInstanceId,
        List.of(1L, 2L),
        "USR-ERR-1",
        "needs correction",
        VariablesDTO.of("field", "email"),
        "jwt-explicit");

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> triggerRecord = capture();
    assertThat(triggerRecord.key()).isEqualTo(processInstanceId);
    assertThat(triggerRecord.value()).isInstanceOf(UserTaskResponseTriggerDTO.class);
    assertThat(headerValue(triggerRecord)).isEqualTo("jwt-explicit");

    UserTaskResponseTriggerDTO trigger = (UserTaskResponseTriggerDTO) triggerRecord.value();
    assertThat(trigger.getMessageId()).isNotBlank();
    assertThat(trigger.getElementInstanceIdPath()).containsExactly(1L, 2L);
    assertThat(Variables.toJavaObject(trigger.getVariables().get("field"))).isEqualTo("email");

    UserTaskResponseResultDTO result = trigger.getUserTaskResponseResult();
    assertThat(result.getResponseType()).isEqualTo(UserTaskResponseType.ERROR);
    assertThat(result.getCode()).isEqualTo("USR-ERR-1");
    assertThat(result.getMessage()).isEqualTo("needs correction");
  }

  @Test
  void escalateUserTask_usesAuthorizationTokenProviderWhenExplicitTokenMissing() {
    AuthorizationTokenProvider provider =
        request -> {
          assertThat(request.scope()).isEqualTo(CommandAuthorizationScope.USER_TASK_COMPLETE);
          assertThat(request.elementInstanceIdPath()).containsExactly(5L, 6L);
          return "jwt-from-provider";
        };
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, provider);
    UUID processInstanceId = UUID.randomUUID();

    responder.escalateUserTask(
        processInstanceId,
        List.of(5L, 6L),
        "USR-ESC-1",
        "supervisor review",
        VariablesDTO.of("priority", "urgent"),
        null);

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> triggerRecord = capture();
    assertThat(triggerRecord.key()).isEqualTo(processInstanceId);
    assertThat(triggerRecord.value()).isInstanceOf(UserTaskResponseTriggerDTO.class);
    assertThat(headerValue(triggerRecord)).isEqualTo("jwt-from-provider");

    UserTaskResponseTriggerDTO trigger = (UserTaskResponseTriggerDTO) triggerRecord.value();
    assertThat(trigger.getMessageId()).isNotBlank();
    assertThat(trigger.getElementInstanceIdPath()).containsExactly(5L, 6L);
    assertThat(Variables.toJavaObject(trigger.getVariables().get("priority"))).isEqualTo("urgent");

    UserTaskResponseResultDTO result = trigger.getUserTaskResponseResult();
    assertThat(result.getResponseType()).isEqualTo(UserTaskResponseType.ESCALATION);
    assertThat(result.getCode()).isEqualTo("USR-ESC-1");
    assertThat(result.getMessage()).isEqualTo("supervisor review");
  }

  @Test
  void errorExternalTask_explicitTokenAddsAuthorizationHeaderAndErrorPayload() {
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, null);
    UUID processInstanceId = UUID.randomUUID();

    responder.errorExternalTask(
        processInstanceId,
        List.of(3L, 4L),
        "ERR-42",
        "business failure",
        VariablesDTO.of("reason", "validation"),
        "jwt-explicit");

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> triggerRecord = capture();
    assertThat(triggerRecord.key()).isEqualTo(processInstanceId);
    assertThat(triggerRecord.value()).isInstanceOf(ExternalTaskResponseTriggerDTO.class);
    assertThat(headerValue(triggerRecord)).isEqualTo("jwt-explicit");

    ExternalTaskResponseTriggerDTO trigger = (ExternalTaskResponseTriggerDTO) triggerRecord.value();
    assertThat(trigger.getMessageId()).isNotBlank();
    assertThat(trigger.getElementInstanceIdPath()).containsExactly(3L, 4L);
    assertThat(Variables.toJavaObject(trigger.getVariables().get("reason")))
        .isEqualTo("validation");

    ExternalTaskResponseResultDTO result = trigger.getExternalTaskResponseResult();
    assertThat(result.getResponseType()).isEqualTo(ExternalTaskResponseType.ERROR);
    assertThat(result.getAllowRetry()).isFalse();
    assertThat(result.getCode()).isEqualTo("ERR-42");
    assertThat(result.getMessage()).isEqualTo("business failure");
    assertThat(result.getTimeout()).isZero();
  }

  @Test
  void escalateExternalTask_usesAuthorizationTokenProviderWhenExplicitTokenMissing() {
    AuthorizationTokenProvider provider =
        request -> {
          assertThat(request.scope()).isEqualTo(CommandAuthorizationScope.EXTERNAL_TASK_COMPLETE);
          assertThat(request.elementInstanceIdPath()).containsExactly(7L, 8L);
          return "jwt-from-provider";
        };
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, provider);
    UUID processInstanceId = UUID.randomUUID();

    responder.escalateExternalTask(
        processInstanceId,
        List.of(7L, 8L),
        "ESC-9",
        "needs escalation",
        VariablesDTO.of("priority", "high"),
        null);

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> triggerRecord = capture();
    assertThat(triggerRecord.key()).isEqualTo(processInstanceId);
    assertThat(triggerRecord.value()).isInstanceOf(ExternalTaskResponseTriggerDTO.class);
    assertThat(headerValue(triggerRecord)).isEqualTo("jwt-from-provider");

    ExternalTaskResponseTriggerDTO trigger = (ExternalTaskResponseTriggerDTO) triggerRecord.value();
    assertThat(trigger.getMessageId()).isNotBlank();
    assertThat(trigger.getElementInstanceIdPath()).containsExactly(7L, 8L);
    assertThat(Variables.toJavaObject(trigger.getVariables().get("priority"))).isEqualTo("high");

    ExternalTaskResponseResultDTO result = trigger.getExternalTaskResponseResult();
    assertThat(result.getResponseType()).isEqualTo(ExternalTaskResponseType.ESCALATION);
    assertThat(result.getAllowRetry()).isTrue();
    assertThat(result.getCode()).isEqualTo("ESC-9");
    assertThat(result.getMessage()).isEqualTo("needs escalation");
    assertThat(result.getTimeout()).isZero();
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

  @Test
  void completeExternalTask_guardBlocksProtectedTrafficBeforeSend() {
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, null);
    responder.setProtectedDataPlaneGuard(
        (operation, explicitAuthorizationToken) -> {
          throw new IllegalStateException("blocked by policy");
        });

    assertThatThrownBy(
            () ->
                responder.completeExternalTask(
                    UUID.randomUUID(), List.of(1L), VariablesDTO.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("blocked by policy");

    verify(producer, never()).send(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void responderForExternalTask_guardBlocksResponderObjectBeforeSend() {
    ProcessInstanceResponder responder =
        new ProcessInstanceResponder(propertiesHelper, producer, null);
    responder.setProtectedDataPlaneGuard(
        (operation, explicitAuthorizationToken) -> {
          throw new IllegalStateException("blocked by policy");
        });

    assertThatThrownBy(
            () ->
                responder.responderForExternalTask(UUID.randomUUID(), List.of(9L)).respondSuccess())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("blocked by policy");

    verify(producer, never()).send(org.mockito.ArgumentMatchers.any());
  }

  @SuppressWarnings("unchecked")
  private ProducerRecord<UUID, ProcessInstanceTriggerDTO> capture() {
    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(captor.capture());
    return captor.getValue();
  }

  private String headerValue(ProducerRecord<UUID, ProcessInstanceTriggerDTO> triggerRecord) {
    return new String(
        triggerRecord.headers().lastHeader(Constants.HEADER_AUTHORIZATION).value(),
        StandardCharsets.UTF_8);
  }
}

/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExternalTaskInstanceResponderTest {
  private KafkaProducer<UUID, ProcessInstanceTriggerDTO> mockProducer;
  private ExternalTaskInstanceResponder responder;
  private UUID processInstanceId;
  private List<Long> elementInstanceIdPath;
  private String topicName;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    mockProducer = mock(KafkaProducer.class);
    processInstanceId = UUID.randomUUID();
    elementInstanceIdPath = List.of(1001L, 1002L);
    topicName = "test-topic";
    responder =
        new ExternalTaskInstanceResponder(
            mockProducer, topicName, processInstanceId, elementInstanceIdPath);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondSuccess_runsBeforeSendHook() {
    AtomicBoolean beforeSendCalled = new AtomicBoolean(false);
    responder =
        new ExternalTaskInstanceResponder(
            mockProducer,
            topicName,
            processInstanceId,
            elementInstanceIdPath,
            () -> beforeSendCalled.set(true));

    responder.respondSuccess();

    assertThat(beforeSendCalled).isTrue();
    verify(mockProducer).send(org.mockito.ArgumentMatchers.any(ProducerRecord.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondSuccessWithNoVariables() {
    // When
    responder.respondSuccess();

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ProcessInstanceTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO = assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertSuccessResult(resultDTO);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondSuccessWithCustomObject() {
    // Given
    TestDto testDto = new TestDto();
    testDto.value = "test-value";

    // When
    responder.respondSuccess(testDto);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    ProcessInstanceTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO = assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertSuccessResult(resultDTO);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondSuccessWithVariablesMap() {
    // Given
    Map<String, Object> variables = new HashMap<>();
    variables.put("key1", "value1");
    variables.put("key2", 42);

    // When
    responder.respondSuccess(variables);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO =
        assertRecordBasics(externalTaskResponseTriggerRecord);

    assertTriggerBasics(externalTaskResponseTriggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertSuccessResult(resultDTO);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondEscalation() {
    // Given
    String message = "escalation-message";
    String code = "ESC-001";

    // When
    responder.respondEscalation(code, message);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO =
        assertRecordBasics(externalTaskResponseTriggerRecord);

    assertTriggerBasics(externalTaskResponseTriggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertEscalationResult(resultDTO, code, message);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondError() {
    // Given
    boolean allowRetry = true;
    String code = "ERR-001";
    String message = "error-message";

    // When
    responder.respondError(allowRetry, code, message);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO =
        assertRecordBasics(externalTaskResponseTriggerRecord);

    assertTriggerBasics(externalTaskResponseTriggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertErrorResult(resultDTO, allowRetry, code, message);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondPromise() {
    // Given
    Duration duration = Duration.ofMinutes(5);

    // When
    responder.respondPromise(duration);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ProcessInstanceTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO = assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertPromiseResult(resultDTO, duration);
  }

  // Helper assertion methods
  private ExternalTaskResponseTriggerDTO assertRecordBasics(
      ProducerRecord<UUID, ProcessInstanceTriggerDTO> externalTaskResponseTriggerRecord) {
    assertThat(externalTaskResponseTriggerRecord.topic()).isEqualTo(topicName);
    assertThat(externalTaskResponseTriggerRecord.key()).isEqualTo(processInstanceId);
    assertThat(externalTaskResponseTriggerRecord.value())
        .isInstanceOf(ExternalTaskResponseTriggerDTO.class);
    return (ExternalTaskResponseTriggerDTO) externalTaskResponseTriggerRecord.value();
  }

  private ExternalTaskResponseTriggerDTO assertTriggerBasics(ProcessInstanceTriggerDTO triggerDTO) {
    assertThat(triggerDTO.getProcessInstanceId()).isEqualTo(processInstanceId);
    assertThat(triggerDTO).isInstanceOf(ContinueFlowElementTriggerDTO.class);
    assertThat(((ContinueFlowElementTriggerDTO) triggerDTO).getElementInstanceIdPath())
        .isEqualTo(elementInstanceIdPath);
    assertThat(triggerDTO).isInstanceOf(ExternalTaskResponseTriggerDTO.class);
    return (ExternalTaskResponseTriggerDTO) triggerDTO;
  }

  private void assertSuccessResult(ExternalTaskResponseResultDTO resultDTO) {
    assertThat(resultDTO.getResponseType()).isEqualTo(ExternalTaskResponseType.SUCCESS);
    assertThat(resultDTO.getAllowRetry()).isTrue();
    assertThat(resultDTO.getMessage()).isNull();
    assertThat(resultDTO.getCode()).isNull();
    assertThat(resultDTO.getTimeout()).isZero();
  }

  private void assertEscalationResult(
      ExternalTaskResponseResultDTO resultDTO, String code, String message) {
    assertThat(resultDTO.getResponseType()).isEqualTo(ExternalTaskResponseType.ESCALATION);
    assertThat(resultDTO.getAllowRetry()).isTrue();
    assertThat(resultDTO.getMessage()).isEqualTo(message);
    assertThat(resultDTO.getCode()).isEqualTo(code);
    assertThat(resultDTO.getTimeout()).isZero();
  }

  private void assertErrorResult(
      ExternalTaskResponseResultDTO resultDTO, boolean allowRetry, String code, String message) {
    assertThat(resultDTO.getResponseType()).isEqualTo(ExternalTaskResponseType.ERROR);
    assertThat(resultDTO.getAllowRetry()).isEqualTo(allowRetry);
    assertThat(resultDTO.getMessage()).isEqualTo(message);
    assertThat(resultDTO.getCode()).isEqualTo(code);
    assertThat(resultDTO.getTimeout()).isZero();
  }

  private void assertPromiseResult(ExternalTaskResponseResultDTO resultDTO, Duration duration) {
    assertThat(resultDTO.getResponseType()).isEqualTo(ExternalTaskResponseType.PROMISE);
    assertThat(resultDTO.getAllowRetry()).isTrue();
    assertThat(resultDTO.getMessage()).isNull();
    assertThat(resultDTO.getCode()).isNull();
    assertThat(resultDTO.getTimeout()).isEqualTo(duration.toMillis());
  }

  static class TestDto {
    private String value;

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }
}

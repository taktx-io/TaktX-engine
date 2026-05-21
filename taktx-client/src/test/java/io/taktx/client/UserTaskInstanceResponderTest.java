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

import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import io.taktx.serdes.ProcessInstanceTriggerProtoMapper;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserTaskInstanceResponderTest {

  private KafkaProducer<UUID, ProcessInstanceTriggerDTO> mockProducer;
  private UserTaskInstanceResponder responder;
  private UUID processInstanceId;
  private List<Long> elementInstanceIdPath;
  private String topicName;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    mockProducer = mock(KafkaProducer.class);
    processInstanceId = UUID.randomUUID();
    elementInstanceIdPath = List.of(101L, 202L);
    topicName = "test-topic";
    responder =
        new UserTaskInstanceResponder(
            mockProducer, topicName, processInstanceId, elementInstanceIdPath);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondSuccess_autoPopulatesMessageId() throws Exception {
    responder.respondSuccess();

    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> producedRecord = captor.getValue();
    assertThat(producedRecord.topic()).isEqualTo(topicName);
    assertThat(producedRecord.key()).isEqualTo(processInstanceId);
    assertThat(producedRecord.value()).isInstanceOf(UserTaskResponseTriggerDTO.class);

    UserTaskResponseTriggerDTO trigger = (UserTaskResponseTriggerDTO) producedRecord.value();
    assertThat(trigger.getProcessInstanceId()).isEqualTo(processInstanceId);
    assertThat(trigger.getElementInstanceIdPath()).isEqualTo(elementInstanceIdPath);
    assertThat(trigger.getMessageId()).isNotBlank();
    assertThat(trigger.getUserTaskResponseResult().getResponseType())
        .isEqualTo(UserTaskResponseType.COMPLETED);

    byte[] payload = ProcessInstanceTriggerProtoMapper.toProto(trigger).toByteArray();
    ProcessInstanceTriggerEnvelope envelope = ProcessInstanceTriggerEnvelope.parseFrom(payload);
    assertThat(envelope.hasUserTaskResponse()).isTrue();
    assertThat(envelope.getUserTaskResponse().getMessageId()).isEqualTo(trigger.getMessageId());
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondError_autoPopulatesMessageId() {
    responder.respondError("ERR-1", "Something went wrong");

    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    UserTaskResponseTriggerDTO trigger = (UserTaskResponseTriggerDTO) captor.getValue().value();
    assertThat(trigger.getMessageId()).isNotBlank();
    assertThat(trigger.getUserTaskResponseResult().getResponseType())
        .isEqualTo(UserTaskResponseType.ERROR);
    assertThat(trigger.getUserTaskResponseResult().getCode()).isEqualTo("ERR-1");
  }
}

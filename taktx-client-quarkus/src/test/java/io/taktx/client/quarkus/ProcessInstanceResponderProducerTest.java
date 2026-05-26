/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.client.ProcessInstanceResponder;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import io.taktx.serdes.ProcessInstanceTriggerProtoMapper;
import io.taktx.util.TaktPropertiesHelper;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessInstanceResponderProducerTest {

  @Mock private TaktPropertiesHelper taktPropertiesHelper;
  @Mock private Instance<KafkaProducer<UUID, ProcessInstanceTriggerDTO>> emitterInstance;
  @Mock private KafkaProducer<UUID, ProcessInstanceTriggerDTO> mockProducer;

  private ProcessInstanceResponderProducer producer;

  @BeforeEach
  void setUp() {
    when(taktPropertiesHelper.getPrefixedTopicName(any()))
        .thenReturn("test.process-instance-trigger");
    when(taktPropertiesHelper.getKafkaProducerProperties()).thenReturn(defaultProducerProperties());
    when(emitterInstance.isUnsatisfied()).thenReturn(false);
    when(emitterInstance.isAmbiguous()).thenReturn(false);
    when(emitterInstance.get()).thenReturn(mockProducer);
    producer = new ProcessInstanceResponderProducer(taktPropertiesHelper, emitterInstance);
  }

  @SuppressWarnings("unchecked")
  @Test
  void processInstanceResponder_completesUserTaskUsingInjectedProducer() {
    ProcessInstanceResponder responder = producer.processInstanceResponder();
    UUID processInstanceId = UUID.randomUUID();
    List<Long> elementPath = List.of(100L, 200L);

    responder
        .responderForUserTaskTrigger(
            new UserTaskTriggerDTO(
                processInstanceId, null, "approve-order", elementPath, null, null, null, null))
        .respondSuccess();

    ArgumentCaptor<ProducerRecord<UUID, ProcessInstanceTriggerDTO>> recordCaptor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(recordCaptor.capture());

    ProducerRecord<UUID, ProcessInstanceTriggerDTO> producedRecord = recordCaptor.getValue();
    assertThat(producedRecord.topic()).isEqualTo("test.process-instance-trigger");
    assertThat(producedRecord.key()).isEqualTo(processInstanceId);

    ProcessInstanceTriggerEnvelope envelope =
        ProcessInstanceTriggerProtoMapper.toProto(producedRecord.value());
    assertThat(envelope.hasUserTaskResponse()).isTrue();
    assertThat(envelope.getUserTaskResponse().getProcessInstanceId().getHigh())
        .isEqualTo(processInstanceId.getMostSignificantBits());
  }

  @Test
  void processInstanceResponder_isCreatedWhenEmitterInstanceIsNull() {
    ProcessInstanceResponderProducer localProducer =
        new ProcessInstanceResponderProducer(taktPropertiesHelper, null);

    ProcessInstanceResponder responder = localProducer.processInstanceResponder();

    assertThat(responder).isNotNull();
  }

  @Test
  void processInstanceResponder_isCreatedWhenEmitterInstanceIsUnsatisfied() {
    when(emitterInstance.isUnsatisfied()).thenReturn(true);

    ProcessInstanceResponder responder = producer.processInstanceResponder();

    assertThat(responder).isNotNull();
  }

  @Test
  void processInstanceResponder_isCreatedWhenEmitterInstanceIsAmbiguous() {
    when(emitterInstance.isUnsatisfied()).thenReturn(false);
    when(emitterInstance.isAmbiguous()).thenReturn(true);

    ProcessInstanceResponder responder = producer.processInstanceResponder();

    assertThat(responder).isNotNull();
  }

  private static Properties defaultProducerProperties() {
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    return properties;
  }
}

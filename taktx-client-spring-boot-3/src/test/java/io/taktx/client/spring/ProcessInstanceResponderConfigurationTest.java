/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.client.ProcessInstanceResponder;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import io.taktx.serdes.ProcessInstanceTriggerProtoMapper;
import io.taktx.util.TaktPropertiesHelper;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ProcessInstanceResponderConfigurationTest {

  @SuppressWarnings("unchecked")
  @Test
  void processInstanceResponderBean_completesUserTaskUsingInjectedProducer() {
    TaktPropertiesHelper taktPropertiesHelper = mock(TaktPropertiesHelper.class);
    KafkaProducer<UUID, ProcessInstanceTriggerDTO> mockProducer = mock(KafkaProducer.class);
    when(taktPropertiesHelper.getPrefixedTopicName(any()))
        .thenReturn("test.process-instance-trigger");

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(TaktPropertiesHelper.class, () -> taktPropertiesHelper);
      context.registerBean(
          "processInstanceTriggerEmitter", KafkaProducer.class, () -> mockProducer);
      context.register(ProcessInstanceResponderConfiguration.class);
      context.refresh();

      ProcessInstanceResponder responder = context.getBean(ProcessInstanceResponder.class);
      UUID processInstanceId = UUID.randomUUID();
      List<Long> elementPath = List.of(11L, 22L);

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
      assertThat(envelope.getUserTaskResponse().getMessageId()).isNotBlank();
    }
  }
}

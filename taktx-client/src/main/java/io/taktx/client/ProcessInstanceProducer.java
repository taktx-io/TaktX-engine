/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.ProcessInstanceTriggerSerializer;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TerminateTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDSerializer;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class ProcessInstanceProducer {

  private final TaktPropertiesHelper kafkaPropertiesHelper;
  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter;

  public ProcessInstanceProducer(TaktPropertiesHelper kafkaPropertiesHelper) {
    this.kafkaPropertiesHelper = kafkaPropertiesHelper;

    processInstanceTriggerEmitter =
        new KafkaProducer<>(
            kafkaPropertiesHelper.getKafkaProducerProperties(
                TaktUUIDSerializer.class, ProcessInstanceTriggerSerializer.class));
  }

  public UUID startProcess(String processDefinitionId, VariablesDTO variables) {
    UUID processInstanceId = UUID.randomUUID();
    StartCommandDTO startCommand =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            new ProcessDefinitionKey(processDefinitionId),
            variables);
    processInstanceTriggerEmitter.send(
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            processInstanceId,
            startCommand));
    return processInstanceId;
  }

  public void terminateProcessInstance(UUID processInstanceId) {
    terminateElementInstance(processInstanceId, List.of());
  }

  public void terminateElementInstance(UUID processInstanceId, List<Long> elementInstanceIdPath) {
    TerminateTriggerDTO terminateTrigger =
        new TerminateTriggerDTO(processInstanceId, elementInstanceIdPath);
    processInstanceTriggerEmitter.send(
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            processInstanceId,
            terminateTrigger));
  }
}

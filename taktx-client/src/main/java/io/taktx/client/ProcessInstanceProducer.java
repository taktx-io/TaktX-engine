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
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDSerializer;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * A producer for process instance triggers, responsible for producing and sending
 * ProcessInstanceTriggerDTO objects to a Kafka topic.
 */
public class ProcessInstanceProducer {

  private final TaktPropertiesHelper kafkaPropertiesHelper;
  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter;

  /**
   * Constructor for ProcessInstanceProducer.
   *
   * @param kafkaPropertiesHelper the TaktPropertiesHelper to use for configuration
   */
  public ProcessInstanceProducer(TaktPropertiesHelper kafkaPropertiesHelper) {
    this.kafkaPropertiesHelper = kafkaPropertiesHelper;

    processInstanceTriggerEmitter =
        new KafkaProducer<>(
            kafkaPropertiesHelper.getKafkaProducerProperties(
                TaktUUIDSerializer.class, ProcessInstanceTriggerSerializer.class));
  }

  /**
   * Starts a new process instance by sending a StartCommandDTO to the configured Kafka topic.
   *
   * @param processDefinitionId the ID of the process definition to start
   * @param variables the initial variables for the process instance
   * @return the UUID of the started process instance
   */
  public UUID startProcess(String processDefinitionId, VariablesDTO variables) {
    return startProcess(processDefinitionId, -1, variables);
  }

  /**
   * Starts a new process instance by sending a StartCommandDTO to the configured Kafka topic.
   *
   * @param processDefinitionId the ID of the process definition to start
   * @param version the version of the process definition to start, -1 for latest
   * @param variables the initial variables for the process instance
   * @return the UUID of the started process instance
   */
  public UUID startProcess(String processDefinitionId, int version, VariablesDTO variables) {
    UUID processInstanceId = UUID.randomUUID();
    StartCommandDTO startCommand =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            new ProcessDefinitionKey(processDefinitionId, version),
            variables);
    processInstanceTriggerEmitter.send(
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            processInstanceId,
            startCommand));
    return processInstanceId;
  }

  /**
   * Aborts a process instance by sending an AbortTriggerDTO to the configured Kafka topic.
   *
   * @param processInstanceId the UUID of the process instance to abort
   */
  public void abortProcessInstance(UUID processInstanceId) {
    abortElementInstance(processInstanceId, List.of());
  }

  /**
   * Aborts a process instance by sending an SetVariableTriggerDTO to the configured Kafka topic.
   *
   * @param processInstanceId the UUID of the process instance to abort
   */
  public void setVariable(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    SetVariableTriggerDTO setVariableTrigger =
        new SetVariableTriggerDTO(processInstanceId, elementInstanceIdPath, variables);
    processInstanceTriggerEmitter.send(
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            processInstanceId,
            setVariableTrigger));
  }

  /**
   * Aborts a specific element instance within a process instance by sending an AbortTriggerDTO to
   * the configured Kafka topic.
   *
   * @param processInstanceId the UUID of the process instance containing the element instance
   * @param elementInstanceIdPath the path of element instance IDs leading to the target element
   *     instance to abort
   */
  public void abortElementInstance(UUID processInstanceId, List<Long> elementInstanceIdPath) {
    AbortTriggerDTO terminateTrigger =
        new AbortTriggerDTO(processInstanceId, elementInstanceIdPath);
    processInstanceTriggerEmitter.send(
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            processInstanceId,
            terminateTrigger));
  }
}

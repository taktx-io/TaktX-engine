/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.Topics;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.util.TaktPropertiesHelper;
import jakarta.annotation.Nullable;
import java.nio.charset.StandardCharsets;
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
  public ProcessInstanceProducer(
      TaktPropertiesHelper kafkaPropertiesHelper,
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter) {
    this.kafkaPropertiesHelper = kafkaPropertiesHelper;

    this.processInstanceTriggerEmitter = processInstanceTriggerEmitter;
  }

  /**
   * Starts a new process instance by sending a StartCommandDTO to the configured Kafka topic.
   *
   * @param processDefinitionId the ID of the process definition to start
   * @param variables the initial variables for the process instance
   * @return the UUID of the started process instance
   */
  public UUID startProcess(String processDefinitionId, VariablesDTO variables) {
    return startProcess(processDefinitionId, -1, variables, null);
  }

  public UUID startProcess(String processDefinitionId, int version, VariablesDTO variables) {
    return startProcess(processDefinitionId, version, variables, null);
  }

  /**
   * Starts a new process instance, optionally attaching a Platform Service authorization token.
   *
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null} for
   *     unauthenticated deployments
   */
  public UUID startProcess(
      String processDefinitionId,
      int version,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    UUID processInstanceId = UUID.randomUUID();
    StartCommandDTO startCommand =
        new StartCommandDTO(
            processInstanceId,
            null,
            null,
            new ProcessDefinitionKey(processDefinitionId, version),
            variables);
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerRecord =
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            processInstanceId,
            startCommand);
    if (authorizationToken != null && !authorizationToken.isBlank()) {
      processInstanceTriggerRecord
          .headers()
          .add("X-TaktX-Authorization", authorizationToken.getBytes(StandardCharsets.UTF_8));
    }
    processInstanceTriggerEmitter.send(processInstanceTriggerRecord);
    return processInstanceId;
  }

  /**
   * Aborts a process instance by sending an AbortTriggerDTO to the configured Kafka topic.
   *
   * @param processInstanceId the UUID of the process instance to abort
   */
  public void abortProcessInstance(UUID processInstanceId) {
    abortElementInstance(processInstanceId, List.of(), null);
  }

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

  public void abortElementInstance(UUID processInstanceId, List<Long> elementInstanceIdPath) {
    abortElementInstance(processInstanceId, elementInstanceIdPath, null);
  }

  /**
   * Aborts an element instance, optionally attaching a Platform Service authorization token.
   *
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void abortElementInstance(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      @Nullable String authorizationToken) {
    AbortTriggerDTO terminateTrigger =
        new AbortTriggerDTO(processInstanceId, elementInstanceIdPath);
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerRecord =
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            processInstanceId,
            terminateTrigger);
    if (authorizationToken != null && !authorizationToken.isBlank()) {
      processInstanceTriggerRecord
          .headers()
          .add("X-TaktX-Authorization", authorizationToken.getBytes(StandardCharsets.UTF_8));
    }
    processInstanceTriggerEmitter.send(processInstanceTriggerRecord);
  }
}

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
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDSerializer;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;

/**
 * A responder for process instance triggers, responsible for creating responders for different
 * types of flow element triggers.
 */
public class ProcessInstanceResponder {

  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> responseEmitter;
  private final String topicName;

  /**
   * Constructor for ProcessInstanceResponder.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   */
  public ProcessInstanceResponder(TaktPropertiesHelper taktPropertiesHelper) {
    this.topicName =
        taktPropertiesHelper.getPrefixedTopicName(
            Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());
    this.responseEmitter =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(
                TaktUUIDSerializer.class, ProcessInstanceTriggerSerializer.class));
  }

  /**
   * Constructor for ProcessInstanceResponder.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param processInstanceTriggerEmitter the Kafka producer to emit process instance triggers
   */
  public ProcessInstanceResponder(
      TaktPropertiesHelper taktPropertiesHelper,
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter) {
    this.topicName =
        taktPropertiesHelper.getPrefixedTopicName(
            Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());
    this.responseEmitter = processInstanceTriggerEmitter;
  }

  /**
   * Creates an ExternalTaskInstanceResponder for the given ExternalTaskTriggerDTO.
   *
   * @param externalTaskTriggerDTO the ExternalTaskTriggerDTO to create the responder for
   * @return the created ExternalTaskInstanceResponder
   */
  public ExternalTaskInstanceResponder responderForExternalTaskTrigger(
      ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return new ExternalTaskInstanceResponder(
        responseEmitter,
        topicName,
        externalTaskTriggerDTO.getProcessInstanceId(),
        externalTaskTriggerDTO.getElementInstanceIdPath());
  }

  /**
   * Creates an ExternalTaskInstanceResponder for the given ExternalTaskTriggerDTO.
   *
   * @param processInstanceId process instance id
   * @param elementInstanceIdPath the path to the element instance id
   * @return the created ExternalTaskInstanceResponder
   */
  public ExternalTaskInstanceResponder responderForExternalTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return new ExternalTaskInstanceResponder(
        responseEmitter, topicName, processInstanceId, elementInstanceIdPath);
  }

  /**
   * Creates a UserTaskInstanceResponder for the given UserTaskTriggerDTO.
   *
   * @param userTaskTriggerDTO the UserTaskTriggerDTO to create the responder for
   * @return the created UserTaskInstanceResponder
   */
  public UserTaskInstanceResponder responderForUserTaskTrigger(
      UserTaskTriggerDTO userTaskTriggerDTO) {
    return new UserTaskInstanceResponder(
        responseEmitter,
        topicName,
        userTaskTriggerDTO.getProcessInstanceId(),
        userTaskTriggerDTO.getElementInstanceIdPath());
  }
}

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
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDSerializer;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;

public class ProcessInstanceResponder {

  private final KafkaProducer<UUID, ContinueFlowElementTriggerDTO> responseEmitter;
  private final String topicName;

  public ProcessInstanceResponder(TaktPropertiesHelper taktPropertiesHelper) {
    this.topicName =
        taktPropertiesHelper.getPrefixedTopicName(
            Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());
    this.responseEmitter =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(
                TaktUUIDSerializer.class, ProcessInstanceTriggerSerializer.class));
  }

  public ExternalTaskInstanceResponder responderForExternalTaskTrigger(
      ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return new ExternalTaskInstanceResponder(
        responseEmitter,
        topicName,
        externalTaskTriggerDTO.getProcessInstanceKey(),
        externalTaskTriggerDTO.getElementInstanceIdPath());
  }

  public UserTaskInstanceResponder responderForUserTaskTrigger(
      UserTaskTriggerDTO userTaskTriggerDTO) {
    return new UserTaskInstanceResponder(
        responseEmitter,
        topicName,
        userTaskTriggerDTO.getProcessInstanceKey(),
        userTaskTriggerDTO.getElementInstanceIdPath());
  }
}

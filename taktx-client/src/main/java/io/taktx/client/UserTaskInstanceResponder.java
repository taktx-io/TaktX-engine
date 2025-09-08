/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class UserTaskInstanceResponder {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());
  private final KafkaProducer<UUID, ContinueFlowElementTriggerDTO> responseEmitter;
  private final String topicName;
  private final UUID processInstanceId;
  private final List<Long> elementInstanceIdPath;

  public UserTaskInstanceResponder(
      KafkaProducer<UUID, ContinueFlowElementTriggerDTO> responseEmitter,
      String topicName,
      UUID processInstanceId,
      List<Long> elementInstanceIdPath) {
    this.responseEmitter = responseEmitter;
    this.topicName = topicName;
    this.processInstanceId = processInstanceId;
    this.elementInstanceIdPath = elementInstanceIdPath;
  }

  public void respondSuccess() {
    Map<String, JsonNode> variablesMap = new HashMap<>();
    respondSuccess(variablesMap);
  }

  public void respondSuccess(Object variable) {
    Map<String, JsonNode> variablesMap =
        variable == null
            ? Map.of()
            : OBJECT_MAPPER.convertValue(
                variable, new TypeReference<LinkedHashMap<String, JsonNode>>() {});
    respondSuccess(variablesMap);
  }

  public void respondSuccess(Map<String, JsonNode> variablesMap) {
    UserTaskResponseResultDTO userTaskResponseResult =
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null);
    UserTaskResponseTriggerDTO processInstanceTrigger =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            userTaskResponseResult,
            new VariablesDTO(variablesMap));
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceId(), processInstanceTrigger));
  }

  public void respondEscalation(String code, String message) {
    respondEscalation(code, message, VariablesDTO.empty());
  }

  public void respondEscalation(String code, String message, VariablesDTO variables) {
    UserTaskResponseTriggerDTO processInstanceTrigger =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            new UserTaskResponseResultDTO(UserTaskResponseType.ESCALATION, code, message),
            variables);
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceId(), processInstanceTrigger));
  }

  public void respondError(String code, String message, VariablesDTO variables) {

    UserTaskResponseTriggerDTO processInstanceTrigger =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            new UserTaskResponseResultDTO(UserTaskResponseType.ERROR, code, message),
            variables);
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceId(), processInstanceTrigger));
  }

  public void respondError(String code, String message) {
    respondError(code, message, VariablesDTO.empty());
  }
}

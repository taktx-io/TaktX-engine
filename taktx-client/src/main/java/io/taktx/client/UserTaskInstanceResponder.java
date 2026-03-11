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
import io.taktx.dto.ProcessInstanceTriggerDTO;
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

/**
 * A responder for user task instances that allows sending success, escalation, or error responses
 * back to the process instance via Kafka.
 */
public class UserTaskInstanceResponder {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());
  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> responseEmitter;
  private final String topicName;
  private final UUID processInstanceId;
  private final List<Long> elementInstanceIdPath;

  public UserTaskInstanceResponder(
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> responseEmitter,
      String topicName,
      UUID processInstanceId,
      List<Long> elementInstanceIdPath) {
    this.responseEmitter = responseEmitter;
    this.topicName = topicName;
    this.processInstanceId = processInstanceId;
    this.elementInstanceIdPath = elementInstanceIdPath;
  }

  /** Sends a success response with no variables. */
  public void respondSuccess() {
    Map<String, JsonNode> variablesMap = new HashMap<>();
    respondSuccess(variablesMap);
  }

  /**
   * Sends a success response with the provided variables.
   *
   * @param variable the variables to include in the response, can be null
   */
  public void respondSuccess(Object variable) {
    Map<String, JsonNode> variablesMap =
        variable == null
            ? Map.of()
            : OBJECT_MAPPER.convertValue(
                variable, new TypeReference<LinkedHashMap<String, JsonNode>>() {});
    respondSuccess(variablesMap);
  }

  /**
   * Sends a success response with the provided variables map.
   *
   * @param variablesMap the map of variable names to JsonNode values
   */
  public void respondSuccess(Map<String, JsonNode> variablesMap) {
    UserTaskResponseResultDTO userTaskResponseResult =
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null);
    UserTaskResponseTriggerDTO processInstanceTrigger =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            userTaskResponseResult,
            new VariablesDTO(variablesMap));
    sendSigned(processInstanceTrigger);
  }

  /**
   * Sends an escalation response with the provided code and message, and no variables.
   *
   * @param code the escalation code
   * @param message the escalation message
   */
  public void respondEscalation(String code, String message) {
    respondEscalation(code, message, VariablesDTO.empty());
  }

  /**
   * Sends an escalation response with the provided code, message, and variables.
   *
   * @param code the escalation code
   * @param message the escalation message
   * @param variables the variables to include in the response
   */
  public void respondEscalation(String code, String message, VariablesDTO variables) {
    UserTaskResponseTriggerDTO processInstanceTrigger =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            new UserTaskResponseResultDTO(UserTaskResponseType.ESCALATION, code, message),
            variables);
    sendSigned(processInstanceTrigger);
  }

  /**
   * Sends an error response with the provided code and message, and optional variables.
   *
   * @param code the error code
   * @param message the error message
   * @param variables the variables to include in the response
   */
  public void respondError(String code, String message, VariablesDTO variables) {

    UserTaskResponseTriggerDTO processInstanceTrigger =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            new UserTaskResponseResultDTO(UserTaskResponseType.ERROR, code, message),
            variables);
    sendSigned(processInstanceTrigger);
  }

  /**
   * Sends an error response with the provided code and message, and no variables.
   *
   * @param code the error code
   * @param message the error message
   */
  public void respondError(String code, String message) {
    respondError(code, message, VariablesDTO.empty());
  }

  private void sendSigned(UserTaskResponseTriggerDTO trigger) {
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> record =
        new ProducerRecord<>(topicName, trigger.getProcessInstanceId(), trigger);
    responseEmitter.send(record);
  }
}

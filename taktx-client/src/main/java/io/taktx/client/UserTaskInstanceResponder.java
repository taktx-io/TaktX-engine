/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
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

  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> responseEmitter;
  private final String topicName;
  private final UUID processInstanceId;
  private final List<Long> elementInstanceIdPath;
  private final Runnable beforeSendHook;

  public UserTaskInstanceResponder(
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> responseEmitter,
      String topicName,
      UUID processInstanceId,
      List<Long> elementInstanceIdPath) {
    this(responseEmitter, topicName, processInstanceId, elementInstanceIdPath, () -> {});
  }

  UserTaskInstanceResponder(
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> responseEmitter,
      String topicName,
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      Runnable beforeSendHook) {
    this.responseEmitter = responseEmitter;
    this.topicName = topicName;
    this.processInstanceId = processInstanceId;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.beforeSendHook = beforeSendHook != null ? beforeSendHook : () -> {};
  }

  /** Sends a success response with no variables. */
  public void respondSuccess() {
    respondSuccess(VariablesDTO.empty());
  }

  /**
   * Sends a success response with the provided variables.
   *
   * @param variable the variables to include in the response, can be null
   */
  public void respondSuccess(Object variable) {
    respondSuccess(toVariables(variable));
  }

  /**
   * Sends a success response with the provided variables map.
   *
   * @param variablesMap the map of variable names to variable values
   */
  public void respondSuccess(Map<String, JsonNode> variablesMap) {
    respondSuccess(
        variablesMap == null ? VariablesDTO.empty() : VariablesDTO.ofJsonMap(variablesMap));
  }

  private void respondSuccess(VariablesDTO variables) {
    UserTaskResponseResultDTO userTaskResponseResult =
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null);
    UserTaskResponseTriggerDTO processInstanceTrigger =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            UUID.randomUUID().toString(),
            userTaskResponseResult,
            variables);
    sendSigned(processInstanceTrigger);
  }

  @SuppressWarnings("unchecked")
  private static VariablesDTO toVariables(Object variable) {
    if (variable == null) {
      return VariablesDTO.empty();
    }
    return VariablesDTO.ofObjectMap(VariablesDTO.OBJECT_MAPPER.convertValue(variable, Map.class));
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
            UUID.randomUUID().toString(),
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
            UUID.randomUUID().toString(),
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

  private void sendSigned(UserTaskResponseTriggerDTO responseDto) {
    beforeSendHook.run();
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> record =
        new ProducerRecord<>(topicName, responseDto.getProcessInstanceId(), responseDto);
    responseEmitter.send(record);
  }
}

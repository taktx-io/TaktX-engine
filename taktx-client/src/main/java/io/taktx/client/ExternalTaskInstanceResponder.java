/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.VariablesDTO;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * A responder for handling external task instances, allowing to send success, error, escalation, or
 * promise responses back to the process engine via Kafka.
 */
public class ExternalTaskInstanceResponder {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());
  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> responseEmitter;
  private final String topicName;
  private final UUID processInstanceId;
  private final List<Long> elementInstanceIdPath;
  private final Runnable beforeSendHook;

  public ExternalTaskInstanceResponder(
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> responseEmitter,
      String topicName,
      UUID processInstanceId,
      List<Long> elementInstanceIdPath) {
    this(responseEmitter, topicName, processInstanceId, elementInstanceIdPath, () -> {});
  }

  ExternalTaskInstanceResponder(
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

  /** Responds with a success message without any variables. */
  public void respondSuccess() {
    Map<String, JsonNode> variablesMap = new HashMap<>();
    respondSuccess(variablesMap);
  }

  /**
   * Responds with a success message including the provided variables.
   *
   * @param variable The variables to include in the response. It is directly serialized to a JSON
   *     map
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
   * Responds with a success message including the provided variables map.
   *
   * @param variablesMap The map of variable names to their JSON values.
   */
  public void respondSuccess(Map<String, JsonNode> variablesMap) {
    ExternalTaskResponseResultDTO externalTaskResponseResult =
        new ExternalTaskResponseResultDTO(ExternalTaskResponseType.SUCCESS, true, null, null, 0L);
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            externalTaskResponseResult,
            new VariablesDTO(variablesMap));
    sendSigned(processInstanceTrigger);
  }

  /**
   * Responds with an escalation message without any variables.
   *
   * @param code The escalation code.
   * @param message The escalation message.
   */
  public void respondEscalation(String code, String message) {
    respondEscalation(code, message, VariablesDTO.empty());
  }

  /**
   * Responds with an escalation message including the provided variables.
   *
   * @param code The escalation code.
   * @param message The escalation message.
   * @param variables The variables to include in the response.
   */
  public void respondEscalation(String code, String message, VariablesDTO variables) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ESCALATION, true, code, message, 0L),
            variables);
    sendSigned(processInstanceTrigger);
  }

  /**
   * Responds with an error message.
   *
   * @param allowRetry Whether to allow retrying the task.
   * @param code The error code.
   * @param message The error message.
   */
  public void respondError(boolean allowRetry, String code, String message) {
    respondError(allowRetry, code, message, VariablesDTO.empty());
  }

  /**
   * Responds with an error message.
   *
   * @param allowRetry Whether to allow retrying the task.
   * @param code The error code.
   * @param message The error message.
   * @param variables The variables to include in the response.
   */
  public void respondError(
      boolean allowRetry, String code, String message, VariablesDTO variables) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ERROR, allowRetry, code, message, 0L),
            variables);
    sendSigned(processInstanceTrigger);
  }

  /**
   * Responds with an error message.
   *
   * @param message The error message.
   * @param stackTrace the stack trace
   */
  public void respondIncident(String message, String[] stackTrace) {
    respondIncident(message, stackTrace, VariablesDTO.empty());
  }

  /**
   * Responds with an incident message which will directly end the running process instance.
   *
   * @param message The error message.
   * @param stackTrace the stack trace
   * @param variables The variables to include in the response.
   */
  public void respondIncident(String message, String[] stackTrace, VariablesDTO variables) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.INCIDENT, false, null, message, 0L, stackTrace),
            variables);
    sendSigned(processInstanceTrigger);
  }

  /**
   * Responds with a promise message indicating the task will be completed after the specified
   * duration.
   *
   * @param duration The duration to wait before completing the task.
   */
  public void respondPromise(Duration duration) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.PROMISE, true, null, null, duration.toMillis()),
            VariablesDTO.empty());
    sendSigned(processInstanceTrigger);
  }

  private void sendSigned(ExternalTaskResponseTriggerDTO trigger) {
    beforeSendHook.run();
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> producerRecord =
        new ProducerRecord<>(
            topicName, null, System.currentTimeMillis(), trigger.getProcessInstanceId(), trigger);
    responseEmitter.send(producerRecord);
  }
}

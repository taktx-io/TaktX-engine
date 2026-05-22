/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.auth.AuthorizationTokenProvider;
import io.taktx.client.auth.CommandAuthorizationRequest;
import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.serdes.ProcessInstanceTriggerProtoMapper;
import io.taktx.serdes.ProtoSigningSerializer;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDSerializer;
import jakarta.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * A responder for process instance triggers, responsible for creating responders for different
 * types of flow element triggers.
 */
public class ProcessInstanceResponder {

  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> responseEmitter;
  private final String topicName;
  private final @Nullable AuthorizationTokenProvider authorizationTokenProvider;
  private volatile Runnable beforeSendHook = () -> {};

  /**
   * Constructor for ProcessInstanceResponder.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   */
  public ProcessInstanceResponder(TaktPropertiesHelper taktPropertiesHelper) {
    this(taktPropertiesHelper, (KafkaProducer<UUID, ProcessInstanceTriggerDTO>) null, null);
  }

  /**
   * Constructor for ProcessInstanceResponder with optional worker response signing.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param processInstanceTriggerEmitter the Kafka producer to emit process instance triggers, or
   *     {@code null} to create a default producer
   */
  public ProcessInstanceResponder(
      TaktPropertiesHelper taktPropertiesHelper,
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter) {
    this(taktPropertiesHelper, processInstanceTriggerEmitter, null);
  }

  /**
   * Constructor for ProcessInstanceResponder with optional worker response signing and JWT lookup.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param processInstanceTriggerEmitter the Kafka producer to emit process instance triggers, or
   *     {@code null} to create a default producer
   * @param authorizationTokenProvider provider used to lazily obtain command JWTs when callers do
   *     not supply one explicitly
   */
  public ProcessInstanceResponder(
      TaktPropertiesHelper taktPropertiesHelper,
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter,
      @Nullable AuthorizationTokenProvider authorizationTokenProvider) {
    this.topicName =
        taktPropertiesHelper.getPrefixedTopicName(
            Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());
    this.authorizationTokenProvider = authorizationTokenProvider;
    this.responseEmitter =
        processInstanceTriggerEmitter != null
            ? processInstanceTriggerEmitter
            : new KafkaProducer<>(
                taktPropertiesHelper.getKafkaProducerProperties(),
                new TaktUUIDSerializer(),
                new ProtoSigningSerializer<>(ProcessInstanceTriggerProtoMapper::toProto));
  }

  void setBeforeSendHook(Runnable beforeSendHook) {
    this.beforeSendHook = beforeSendHook != null ? beforeSendHook : () -> {};
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
        externalTaskTriggerDTO.getElementInstanceIdPath(),
        beforeSendHook);
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
        responseEmitter, topicName, processInstanceId, elementInstanceIdPath, beforeSendHook);
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
        userTaskTriggerDTO.getElementInstanceIdPath(),
        beforeSendHook);
  }

  /**
   * Completes a user task without an explicit authorization token.
   *
   * @param processInstanceId process instance ID owning the user task
   * @param elementInstanceIdPath path identifying the active user-task instance
   * @param variables variables to merge on completion
   */
  public void completeUserTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    completeUserTask(processInstanceId, elementInstanceIdPath, variables, null);
  }

  /**
   * Completes a user task, optionally attaching a Platform Service authorization token.
   *
   * @param processInstanceId process instance ID owning the user task
   * @param elementInstanceIdPath path identifying the active user-task instance
   * @param variables variables to merge on completion
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void completeUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    sendUserTaskResponse(
        processInstanceId,
        elementInstanceIdPath,
        new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null),
        variables,
        authorizationToken);
  }

  /**
   * Completes a user task with a BPMN error without an explicit authorization token.
   *
   * @param processInstanceId process instance ID owning the user task
   * @param elementInstanceIdPath path identifying the active user-task instance
   * @param code BPMN error code
   * @param message BPMN error message
   * @param variables variables to merge with the BPMN error response
   */
  public void errorUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    errorUserTask(processInstanceId, elementInstanceIdPath, code, message, variables, null);
  }

  /**
   * Completes a user task with a BPMN error, optionally attaching a Platform Service authorization
   * token.
   *
   * @param processInstanceId process instance ID owning the user task
   * @param elementInstanceIdPath path identifying the active user-task instance
   * @param code BPMN error code
   * @param message BPMN error message
   * @param variables variables to merge with the BPMN error response
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void errorUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    sendUserTaskResponse(
        processInstanceId,
        elementInstanceIdPath,
        new UserTaskResponseResultDTO(UserTaskResponseType.ERROR, code, message),
        variables,
        authorizationToken);
  }

  /**
   * Completes a user task with a BPMN escalation without an explicit authorization token.
   *
   * @param processInstanceId process instance ID owning the user task
   * @param elementInstanceIdPath path identifying the active user-task instance
   * @param code BPMN escalation code
   * @param message BPMN escalation message
   * @param variables variables to merge with the BPMN escalation response
   */
  public void escalateUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    escalateUserTask(processInstanceId, elementInstanceIdPath, code, message, variables, null);
  }

  /**
   * Completes a user task with a BPMN escalation, optionally attaching a Platform Service
   * authorization token.
   *
   * @param processInstanceId process instance ID owning the user task
   * @param elementInstanceIdPath path identifying the active user-task instance
   * @param code BPMN escalation code
   * @param message BPMN escalation message
   * @param variables variables to merge with the BPMN escalation response
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void escalateUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    sendUserTaskResponse(
        processInstanceId,
        elementInstanceIdPath,
        new UserTaskResponseResultDTO(UserTaskResponseType.ESCALATION, code, message),
        variables,
        authorizationToken);
  }

  private void sendUserTaskResponse(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      UserTaskResponseResultDTO responseResult,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    UserTaskResponseTriggerDTO trigger =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            UUID.randomUUID().toString(),
            responseResult,
            variables == null ? VariablesDTO.empty() : variables);
    send(
        trigger,
        authorizationToken,
        CommandAuthorizationRequest.userTaskComplete(processInstanceId, elementInstanceIdPath));
  }

  /**
   * Completes an external task without an explicit authorization token.
   *
   * @param processInstanceId process instance ID owning the external task
   * @param elementInstanceIdPath path identifying the active external-task instance
   * @param variables variables to merge on completion
   */
  public void completeExternalTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    completeExternalTask(processInstanceId, elementInstanceIdPath, variables, null);
  }

  /**
   * Completes an external task, optionally attaching a Platform Service authorization token.
   *
   * @param processInstanceId process instance ID owning the external task
   * @param elementInstanceIdPath path identifying the active external-task instance
   * @param variables variables to merge on completion
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void completeExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    sendExternalTaskResponse(
        processInstanceId,
        elementInstanceIdPath,
        new ExternalTaskResponseResultDTO(ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
        variables,
        authorizationToken);
  }

  /**
   * Completes an external task with a BPMN error without an explicit authorization token.
   *
   * @param processInstanceId process instance ID owning the external task
   * @param elementInstanceIdPath path identifying the active external-task instance
   * @param code BPMN error code
   * @param message BPMN error message
   * @param variables variables to merge with the BPMN error response
   */
  public void errorExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    errorExternalTask(processInstanceId, elementInstanceIdPath, code, message, variables, null);
  }

  /**
   * Completes an external task with a BPMN error, optionally attaching a Platform Service
   * authorization token.
   *
   * @param processInstanceId process instance ID owning the external task
   * @param elementInstanceIdPath path identifying the active external-task instance
   * @param code BPMN error code
   * @param message BPMN error message
   * @param variables variables to merge with the BPMN error response
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void errorExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    sendExternalTaskResponse(
        processInstanceId,
        elementInstanceIdPath,
        new ExternalTaskResponseResultDTO(ExternalTaskResponseType.ERROR, false, code, message, 0L),
        variables,
        authorizationToken);
  }

  /**
   * Completes an external task with a BPMN escalation without an explicit authorization token.
   *
   * @param processInstanceId process instance ID owning the external task
   * @param elementInstanceIdPath path identifying the active external-task instance
   * @param code BPMN escalation code
   * @param message BPMN escalation message
   * @param variables variables to merge with the BPMN escalation response
   */
  public void escalateExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    escalateExternalTask(processInstanceId, elementInstanceIdPath, code, message, variables, null);
  }

  /**
   * Completes an external task with a BPMN escalation, optionally attaching a Platform Service
   * authorization token.
   *
   * @param processInstanceId process instance ID owning the external task
   * @param elementInstanceIdPath path identifying the active external-task instance
   * @param code BPMN escalation code
   * @param message BPMN escalation message
   * @param variables variables to merge with the BPMN escalation response
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void escalateExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    sendExternalTaskResponse(
        processInstanceId,
        elementInstanceIdPath,
        new ExternalTaskResponseResultDTO(
            ExternalTaskResponseType.ESCALATION, true, code, message, 0L),
        variables,
        authorizationToken);
  }

  private void sendExternalTaskResponse(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      ExternalTaskResponseResultDTO responseResult,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    ExternalTaskResponseTriggerDTO trigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceId,
            elementInstanceIdPath,
            UUID.randomUUID().toString(),
            responseResult,
            variables == null ? VariablesDTO.empty() : variables);
    send(
        trigger,
        authorizationToken,
        CommandAuthorizationRequest.externalTaskComplete(processInstanceId, elementInstanceIdPath));
  }

  private void send(
      ProcessInstanceTriggerDTO trigger,
      @Nullable String explicitAuthorizationToken,
      CommandAuthorizationRequest authorizationRequest) {
    beforeSendHook.run();
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> producerRecord =
        new ProducerRecord<>(topicName, trigger.getProcessInstanceId(), trigger);
    attachAuthorizationHeader(producerRecord, explicitAuthorizationToken, authorizationRequest);
    responseEmitter.send(producerRecord);
  }

  private void attachAuthorizationHeader(
      ProducerRecord<UUID, ProcessInstanceTriggerDTO> producerRecord,
      @Nullable String explicitAuthorizationToken,
      CommandAuthorizationRequest authorizationRequest) {
    String authorizationToken = explicitAuthorizationToken;
    if (authorizationToken == null || authorizationToken.isBlank()) {
      authorizationToken = resolveAuthorizationToken(authorizationRequest);
    }
    if (authorizationToken != null && !authorizationToken.isBlank()) {
      producerRecord
          .headers()
          .add(Constants.HEADER_AUTHORIZATION, authorizationToken.getBytes(StandardCharsets.UTF_8));
    }
  }

  private @Nullable String resolveAuthorizationToken(
      CommandAuthorizationRequest authorizationRequest) {
    if (authorizationTokenProvider == null) {
      return null;
    }
    String authorizationToken =
        authorizationTokenProvider.getAuthorizationToken(authorizationRequest);
    if (authorizationToken == null || authorizationToken.isBlank()) {
      throw new IllegalStateException(
          "AuthorizationTokenProvider returned no token for " + authorizationRequest.scope());
    }
    return authorizationToken;
  }
}

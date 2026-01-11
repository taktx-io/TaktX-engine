/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static com.cronutils.utils.StringUtils.isNumeric;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.Constants;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.RepeatDuration;
import io.taktx.engine.pd.model.ExternalTask;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.ExternalTaskInfo;
import io.taktx.engine.pi.model.ExternalTaskInstance;
import io.taktx.engine.pi.model.ScheduledExternalTaskTriggerTimeoutInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@Setter
@Slf4j
public abstract class ExternalTaskInstanceProcessor<
        E extends ExternalTask, I extends ExternalTaskInstance<E>>
    extends ActivityInstanceProcessor<E, I, ExternalTaskResponseTriggerDTO> {

  protected ExternalTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flownodeInstance,
      String inputFlowId) {
    ExternalTask flowNode = flownodeInstance.getFlowNode();
    String externalTaskId =
        getExternalTaskId(flownodeInstance, flowNode.getWorkerDefinition(), variableScope);
    Map<String, String> headers = flowNode.getHeaders();

    if (failIfTopicDoesNotExist(
        processInstanceProcessingContext, scope, variableScope, flownodeInstance, externalTaskId)) {
      return;
    }

    retryDirectly(
        getExternalTaskInfo(
            externalTaskId, flowNode, flownodeInstance, variableScope, headers, null),
        processInstanceProcessingContext.getInstanceResult());
    flownodeInstance.setState(ExecutionState.ACTIVE);
    flownodeInstance.setAttempt(0);
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I externalTaskInstance,
      ExternalTaskResponseTriggerDTO trigger) {

    ExternalTaskResponseResultDTO responseResult = trigger.getExternalTaskResponseResult();
    InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();

    log.debug(
        "Processing external task response: {} for flowNode {}",
        responseResult,
        externalTaskInstance.getFlowNode().getId());
    if (ExternalTaskResponseType.SUCCESS == responseResult.getResponseType()) {
      handleSuccess(instanceResult, externalTaskInstance);
    } else if (ExternalTaskResponseType.PROMISE == responseResult.getResponseType()) {
      handlePromise(instanceResult, externalTaskInstance, trigger);
    } else if (ExternalTaskResponseType.INCIDENT == responseResult.getResponseType()) {
      handleIncident(instanceResult, externalTaskInstance, trigger);
    } else if (ExternalTaskResponseType.ESCALATION == responseResult.getResponseType()) {
      handleEscalation(
          instanceResult,
          scope.getDirectInstanceResult(),
          externalTaskInstance,
          responseResult,
          trigger.getVariables());
    } else if (ExternalTaskResponseType.TIMEOUT == responseResult.getResponseType()
        || ExternalTaskResponseType.ERROR == responseResult.getResponseType()) {
      handleErrorOrTimeout(
          instanceResult,
          scope.getDirectInstanceResult(),
          externalTaskInstance,
          variableScope,
          externalTaskInstance.getFlowNode().getHeaders(),
          responseResult,
          trigger.getVariables());
    }
  }

  private void handleErrorOrTimeout(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      VariableScope variableScope,
      Map<String, String> headers,
      ExternalTaskResponseResultDTO responseResult,
      VariablesDTO variables) {
    E externalTask = externalTaskInstance.getFlowNode();
    if (externalTask.getRetries() != null) {
      handleRetries(
          instanceResult,
          directInstanceResult,
          externalTaskInstance,
          variableScope,
          headers,
          responseResult,
          externalTask,
          variables);
    } else {
      // No retries allowed
      handleNoRetriesAllowed(
          instanceResult, directInstanceResult, externalTaskInstance, responseResult, variables);
    }
  }

  private void handleRetries(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      VariableScope variableScope,
      Map<String, String> headers,
      ExternalTaskResponseResultDTO responseResult,
      E externalTask,
      VariablesDTO variables) {
    // We have some kind of retry definition
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(externalTask.getRetries(), variableScope);

    if (jsonNode == null || jsonNode.isNull()) {
      // Expression returned null, no retries possible
      handleNoRetriesAllowed(
          instanceResult, directInstanceResult, externalTaskInstance, responseResult, variables);
      return;
    }

    String retryString = jsonNode.asText();

    // Analyze the retry definition
    int retries = -1;
    Optional<Duration> backoff = Optional.empty();
    if (isNumeric(retryString)) {
      // Definition is just a number
      try {
        retries = Integer.parseInt(retryString);
        if (retries > 1000) { // Reasonable maximum
          log.warn("Retry count {} exceeds maximum, capping at 1000", retries);
          retries = 1000;
        }
      } catch (NumberFormatException e) {
        log.error("Invalid retry count format: {}", retryString);
        retries = -1; // Will fail the task
      }
    } else {
      // Definition might be a repeat limit with a backoff time
      try {
        RepeatDuration repeatDuration = RepeatDuration.parse(retryString);
        retries = repeatDuration.getRepetitions();
        backoff = Optional.ofNullable(repeatDuration.getDuration());
      } catch (DateTimeParseException e) {
        // Definition is not a valid repeat duration, since retries is still set
        // to -1 it will fail the task and the process instanceToContinue
      }
    }

    if (externalTaskInstance.increaseAttempt() <= retries
        && Boolean.TRUE.equals(responseResult.getAllowRetry())) {
      // Retry allowed, possibly with backoff
      String externalTaskId =
          getExternalTaskId(
              externalTaskInstance, externalTask.getWorkerDefinition(), variableScope);

      ioMappingProcessor.processInputMappings(externalTask, variableScope);
      if (backoff.isPresent()) {
        // This means for now we do nothing, the retry will be scheduled by the scheduler
        scheduleNextExternalTask(
            externalTaskId,
            backoff.get(),
            externalTask,
            externalTaskInstance,
            variableScope,
            headers,
            instanceResult);
      } else {
        // No backoff time defined, retry directly
        retryDirectly(
            getExternalTaskInfo(
                externalTaskId, externalTask, externalTaskInstance, variableScope, headers, null),
            instanceResult);
      }
    } else {
      // No more retries, either by limit or by disallowing retry by the worker
      handleNoMoreRetries(directInstanceResult, externalTaskInstance, responseResult, variables);
    }
  }

  private static void retryDirectly(
      ExternalTaskInfo externalTaskInfo, InstanceResult instanceResult) {
    instanceResult.addExternalTaskRequest(externalTaskInfo);
  }

  private static <E extends ExternalTask, I extends ExternalTaskInstance<E>>
      void handleNoMoreRetries(
          DirectInstanceResult directInstanceResult,
          I externalTaskInstance,
          ExternalTaskResponseResultDTO responseResult,
          VariablesDTO variables) {
    directInstanceResult.addEvent(
        new ErrorEventSignal(
            externalTaskInstance,
            responseResult.getCode(),
            responseResult.getMessage(),
            variables));
  }

  private void handleNoRetriesAllowed(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      ExternalTaskResponseResultDTO responseResult,
      VariablesDTO variables) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    handleNoMoreRetries(directInstanceResult, externalTaskInstance, responseResult, variables);
  }

  private void handleEscalation(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      ExternalTaskResponseResultDTO responseResult,
      VariablesDTO variables) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    directInstanceResult.addEvent(
        new EscalationEventSignal(
            externalTaskInstance,
            responseResult.getCode(),
            responseResult.getMessage(),
            variables));
  }

  private void handlePromise(
      InstanceResult instanceResult,
      I externalTaskInstance,
      ExternalTaskResponseTriggerDTO trigger) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    instanceResult.addScheduledExternalTaskTriggerTimeout(
        new ScheduledExternalTaskTriggerTimeoutInfo(
            externalTaskInstance, trigger.getExternalTaskResponseResult().getTimeout()));
  }

  private void handleIncident(
      InstanceResult instanceResult,
      I externalTaskInstance,
      ExternalTaskResponseTriggerDTO trigger) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    StringBuilder sb = new StringBuilder(trigger.getExternalTaskResponseResult().getMessage());
    for (String st : trigger.getExternalTaskResponseResult().getStacktrace()) {
      sb.append("\n").append(st);
    }
    throw new ProcessInstanceException(externalTaskInstance, sb.toString());
  }

  private void handleSuccess(InstanceResult instanceResult, I externalTaskInstance) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    externalTaskInstance.setState(ExecutionState.COMPLETED);
  }

  private void cancelTimeoutScheduledTrigger(
      InstanceResult instanceResult, I externalTaskInstance) {
    externalTaskInstance.getScheduledKeys().forEach(instanceResult::cancelSchedule);
    externalTaskInstance.clearScheduledKeys();
  }

  @Override
  protected void processAbortSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I instance) {
    // Nothing to do here
  }

  private String getExternalTaskId(
      I flownodeInstance, String workerDefinition, VariableScope variables) {
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression(workerDefinition, variables);
    if (jsonNode == null || jsonNode.isNull()) {
      throw new ProcessInstanceException(
          flownodeInstance, "External task worker definition expression returned null");
    }
    String text = jsonNode.asText();
    // Sanitize the external task id to make it suitable for a topic name
    return text.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private void scheduleNextExternalTask(
      String workerDefinition,
      Duration backoff,
      ExternalTask externalTask,
      ExternalTaskInstance<?> instance,
      VariableScope variables,
      Map<String, String> headers,
      InstanceResult instanceResult) {
    retryDirectly(
        getExternalTaskInfo(
            workerDefinition, externalTask, instance, variables, headers, backoff.toMillis()),
        instanceResult);
  }

  private static ExternalTaskInfo getExternalTaskInfo(
      String workerDefinition,
      ExternalTask externalTask,
      ExternalTaskInstance<?> instance,
      VariableScope variables,
      Map<String, String> headers,
      Long backoff) {
    return new ExternalTaskInfo(
        workerDefinition, externalTask, instance, headers, variables, backoff);
  }

  private boolean failIfTopicDoesNotExist(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flownodeInstance,
      String externalTaskId) {
    if (!processInstanceProcessingContext
        .getTopicManager()
        .topicExists(Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + externalTaskId)) {
      log.warn(
          "Topic for External task {} is not created, failing external task instanceToContinue {}",
          externalTaskId,
          flownodeInstance);
      InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();
      handleErrorOrTimeout(
          instanceResult,
          scope.getDirectInstanceResult(),
          flownodeInstance,
          variableScope,
          flownodeInstance.getFlowNode().getHeaders(),
          new ExternalTaskResponseResultDTO(
              ExternalTaskResponseType.ERROR, false, "Topic not created", "Topic not created", -1L),
          VariablesDTO.empty());
      return true;
    }
    return false;
  }
}

/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.processor;

import static com.cronutils.utils.StringUtils.isNumeric;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ActtivityStateEnum;
import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.RepeatDuration;
import io.taktx.engine.pd.model.ExternalTask;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.ExternalTaskInfo;
import io.taktx.engine.pi.model.ExternalTaskInstance;
import io.taktx.engine.pi.model.ScheduledExternalTaskTriggerTimeoutInfo;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

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
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flownodeInstance,
      String inputFlowId,
      VariableScope variables) {
    log.info("Starting external task instance {}", flownodeInstance);
    ExternalTask flowNode = flownodeInstance.getFlowNode();
    String externalTaskId = getExternalTaskId(flowNode.getWorkerDefinition(), variables);

    if (!topicExists(processInstanceProcessingContext.getExternalTaskMetaStore(), externalTaskId)) {
      log.warn(
          "Topic for External task {} is not created, failing external task instance {}",
          externalTaskId,
          flownodeInstance);
      InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();
      handleErrorOrTimeout(
          instanceResult,
          flowNodeInstanceProcessingContext.getDirectInstanceResult(),
          flownodeInstance,
          variables,
          new ExternalTaskResponseResultDTO(
              ExternalTaskResponseType.ERROR,
              false,
              "Topic not created",
              "Topic not created",
              "Topic not created",
              -1L));
      return;
    }

    retryDirectly(
        getExternalTaskInfo(externalTaskId, flowNode, flownodeInstance, variables, null),
        processInstanceProcessingContext.getInstanceResult());
    flownodeInstance.setState(ActtivityStateEnum.WAITING);
    flownodeInstance.setAttempt(0);
  }

  private boolean topicExists(
      ReadOnlyKeyValueStore<String, ValueAndTimestamp<TopicMetaDTO>> externalTaskMetaStore,
      String externalTaskId) {
    return externalTaskMetaStore.get(Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + externalTaskId)
        != null;
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      I externalTaskInstance,
      ExternalTaskResponseTriggerDTO trigger,
      VariableScope variables) {

    log.info("Continuing external task instance {}", trigger);
    ExternalTaskResponseResultDTO responseResult = trigger.getExternalTaskResponseResult();
    InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();

    if (ExternalTaskResponseType.SUCCESS == responseResult.getResponseType()) {
      handleSuccess(instanceResult, externalTaskInstance);
    } else if (ExternalTaskResponseType.PROMISE == responseResult.getResponseType()) {
      handlePromise(instanceResult, externalTaskInstance, trigger);
    } else if (ExternalTaskResponseType.ESCALATION == responseResult.getResponseType()) {
      handleEscalation(
          instanceResult,
          flowNodeInstanceProcessingContext.getDirectInstanceResult(),
          externalTaskInstance,
          responseResult);
    } else if (ExternalTaskResponseType.TIMEOUT == responseResult.getResponseType()
        || ExternalTaskResponseType.ERROR == responseResult.getResponseType()) {
      handleErrorOrTimeout(
          instanceResult,
          flowNodeInstanceProcessingContext.getDirectInstanceResult(),
          externalTaskInstance,
          variables,
          responseResult);
    }
  }

  private void handleErrorOrTimeout(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      VariableScope flowNodeInstanceVariables,
      ExternalTaskResponseResultDTO responseResult) {
    E externalTask = externalTaskInstance.getFlowNode();
    if (externalTask.getRetries() != null) {
      handleRetries(
          instanceResult,
          directInstanceResult,
          externalTaskInstance,
          flowNodeInstanceVariables,
          responseResult,
          externalTask);
    } else {
      // No retries allowed
      handleNoRetriesAllowed(
          instanceResult, directInstanceResult, externalTaskInstance, responseResult);
    }
  }

  private void handleRetries(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      VariableScope flowNodeInstanceVariables,
      ExternalTaskResponseResultDTO responseResult,
      E externalTask) {
    // We have some kind of retry definition
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(
            externalTask.getRetries(), flowNodeInstanceVariables);
    String retryString = jsonNode.asText();

    // Analyze the retry definition
    int retries = -1;
    Optional<Duration> backoff = Optional.empty();
    if (isNumeric(retryString)) {
      // Definition is just a number
      retries = Integer.parseInt(retryString);
    } else {
      // Definition might be a repeat limit with a backoff time
      try {
        RepeatDuration repeatDuration = RepeatDuration.parse(retryString);
        retries = repeatDuration.getRepetitions();
        backoff = Optional.ofNullable(repeatDuration.getDuration());
      } catch (DateTimeParseException e) {
        // Definition is not a valid repeat duration, since retries is still set
        // to -1 it will fail the task and the process instance
      }
    }

    if (externalTaskInstance.increaseAttempt() <= retries
        && Boolean.TRUE.equals(responseResult.getAllowRetry())) {
      // Retry allowed, possibly with backoff
      String externalTaskId =
          getExternalTaskId(externalTask.getWorkerDefinition(), flowNodeInstanceVariables);

      ioMappingProcessor.addInputVariables(externalTask, flowNodeInstanceVariables);
      if (backoff.isPresent()) {
        // This means for now we do nothing, the retry will be scheduled by the scheduler
        scheduleNextExternalTask(
            externalTaskId,
            backoff.get(),
            externalTask,
            externalTaskInstance,
            flowNodeInstanceVariables,
            instanceResult);
      } else {
        // No backoff time defined, retry directly
        retryDirectly(
            getExternalTaskInfo(
                externalTaskId,
                externalTask,
                externalTaskInstance,
                flowNodeInstanceVariables,
                null),
            instanceResult);
      }
    } else {
      // No more retries, either by limit or by disallowing retry by the worker
      handleNoMoreRetries(directInstanceResult, externalTaskInstance, responseResult);
    }
  }

  private static void retryDirectly(
      ExternalTaskInfo externalTaskId, InstanceResult instanceResult) {
    instanceResult.addExternalTaskRequest(externalTaskId);
  }

  private static <E extends ExternalTask, I extends ExternalTaskInstance<E>>
      void handleNoMoreRetries(
          DirectInstanceResult directInstanceResult,
          I externalTaskInstance,
          ExternalTaskResponseResultDTO responseResult) {
    directInstanceResult.addEvent(
        new ErrorEventSignal(
            externalTaskInstance,
            responseResult.getName(),
            responseResult.getCode(),
            responseResult.getMessage()));
  }

  private void handleNoRetriesAllowed(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      ExternalTaskResponseResultDTO responseResult) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    handleNoMoreRetries(directInstanceResult, externalTaskInstance, responseResult);
  }

  private void handleEscalation(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      ExternalTaskResponseResultDTO responseResult) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    directInstanceResult.addEvent(
        new EscalationEventSignal(
            externalTaskInstance,
            responseResult.getName(),
            responseResult.getCode(),
            responseResult.getMessage()));
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

  private void handleSuccess(InstanceResult instanceResult, I externalTaskInstance) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    externalTaskInstance.setState(ActtivityStateEnum.FINISHED);
  }

  private void cancelTimeoutScheduledTrigger(
      InstanceResult instanceResult, I externalTaskInstance) {
    externalTaskInstance.getScheduledKeys().forEach(instanceResult::cancelSchedule);
    externalTaskInstance.clearScheduledKeys();
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      DirectInstanceResult directInstanceResult,
      I instance,
      VariableScope variables) {
    // Nothing to do here
  }

  private String getExternalTaskId(String workerDefinition, VariableScope variables) {
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression(workerDefinition, variables);
    return jsonNode.asText();
  }

  private void scheduleNextExternalTask(
      String workerDefinition,
      Duration backoff,
      ExternalTask externalTask,
      ExternalTaskInstance<?> instance,
      VariableScope variables,
      InstanceResult instanceResult) {
    retryDirectly(
        getExternalTaskInfo(
            workerDefinition, externalTask, instance, variables, backoff.toMillis()),
        instanceResult);
  }

  private static ExternalTaskInfo getExternalTaskInfo(
      String workerDefinition,
      ExternalTask externalTask,
      ExternalTaskInstance<?> instance,
      VariableScope variables,
      Long backoff) {
    return new ExternalTaskInfo(workerDefinition, externalTask, instance, variables, backoff);
  }
}

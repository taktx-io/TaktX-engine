/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.CancelCorrelationMessageSubscriptionDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.OneTimeScheduleDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.dto.TerminateTriggerDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.TimerEventDefinitionDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.MessageSchedulerFactory;
import io.taktx.engine.pd.model.NewStartCommand;
import io.taktx.engine.pi.model.CatchEventInstance;
import io.taktx.engine.pi.model.ExternalTaskInfo;
import io.taktx.engine.pi.model.ExternalTaskInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstanceWithScheduleKeys;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ScheduledContinuationInfo;
import io.taktx.engine.pi.model.ScheduledExternalTaskTriggerTimeoutInfo;
import io.taktx.engine.pi.model.ScheduledStartInfo;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.UserTaskInfo;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class Forwarder {

  private final PathExtractor pathExtractor;
  private final MessageSchedulerFactory messageSchedulerFactory;
  private final DtoMapper dtoMapper;
  private final Clock clock;
  private final TaktConfiguration taktConfiguration;

  public void forward(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessDefinitionKey definitionKey,
      ProcessInstanceDTO processInstanceDTO) {
    forwardInstanceUpdates(context, instanceResult);
    forwardExternalTaskRequests(context, instanceResult, definitionKey, processInstanceDTO);
    forwardUserTaskTriggers(context, instanceResult, definitionKey, processInstanceDTO);
    forwardNewStartCommands(context, instanceResult, processInstanceDTO);
    forwardContinuations(context, instanceResult);
    forwardCancelSchedules(context, instanceResult);
    forwardScheduledStarts(context, instanceResult, processInstanceDTO);
    forwardScheduledContinuations(context, instanceResult, processInstanceDTO);
    forwardScheduledExternalTaskTriggerTimeouts(context, instanceResult, processInstanceDTO);
    forwardTerminateCommands(context, instanceResult);
    forwardMessageSubscriptionCommands(context, instanceResult, processInstanceDTO);
  }

  private void forwardInstanceUpdates(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    Queue<InstanceUpdate> instanceUpdates = instanceResult.getInstanceUpdates();
    if (Boolean.parseBoolean(taktConfiguration.getBroadcastInstanceUpdates())) {
      while (!instanceUpdates.isEmpty()) {
        InstanceUpdate instanceUpdate = instanceUpdates.poll();
        context.forward(
            new Record<>(
                instanceUpdate.processInstanceId(), instanceUpdate.update(), clock.millis()));
      }
    } else {
      instanceUpdates.clear();
    }
  }

  private void forwardCancelSchedules(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {

    Queue<ScheduleKeyDTO> cancelSchedules = instanceResult.getCancelSchedules();
    while (!cancelSchedules.isEmpty()) {
      ScheduleKeyDTO scheduledKey = cancelSchedules.poll();
      context.forward(new Record<>(scheduledKey, null, clock.millis()));
    }
  }

  private void forwardScheduledStarts(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstanceDTO processInstance) {
    Queue<ScheduledStartInfo> scheduledStartInfos = instanceResult.getScheduledStartInfos();
    while (!scheduledStartInfos.isEmpty()) {
      ScheduledStartInfo scheduledStartInfo = scheduledStartInfos.poll();
      List<Long> instancePath =
          pathExtractor.getInstancePath(
              scheduledStartInfo.flowNodeInstances().getParentFlowNodeInstance());
      String elementId = scheduledStartInfo.flowNodeToStart().getId();
      StartFlowElementTriggerDTO startFlowElementTrigger =
          new StartFlowElementTriggerDTO(
              processInstance.getProcessInstanceId(),
              instancePath,
              elementId,
              VariablesDTO.empty());

      long now = clock.millis();

      MessageScheduleDTO schedule =
          messageSchedulerFactory.schedule(
              dtoMapper.map(scheduledStartInfo.timerEventDefinition()),
              now,
              startFlowElementTrigger,
              VariableScope.empty());

      TimeBucket bucket = TimeBucket.ofMillis(schedule.getNextExecutionTime(now) - now);
      InstanceScheduleKeyDTO scheduledKey =
          new InstanceScheduleKeyDTO(
              processInstance.getProcessInstanceId(), instancePath, elementId, bucket);
      scheduledStartInfo.flowNodeInstances().addScheduledKey(scheduledKey);
      log.info("Forwarding scheduled start {}", scheduledKey);
      context.forward(new Record<>(scheduledKey, schedule, now));
    }
  }

  private void forwardScheduledContinuations(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstanceDTO processInstance) {
    Queue<ScheduledContinuationInfo> scheduledContinuationInfos =
        instanceResult.getScheduledContinuationInfos();
    while (!scheduledContinuationInfos.isEmpty()) {
      ScheduledContinuationInfo info = scheduledContinuationInfos.poll();
      FlowNodeInstanceWithScheduleKeys catchEventInstance = info.catchEventInstance();
      ContinueFlowElementTriggerDTO continueFlowElementTrigger =
          new ContinueFlowElementTriggerDTO(
              processInstance.getProcessInstanceId(),
              pathExtractor.getInstancePath(catchEventInstance),
              null,
              info.variables().scopeToDTO());

      long now = clock.millis();

      MessageScheduleDTO schedule =
          messageSchedulerFactory.schedule(
              dtoMapper.map(info.timerEventDefinition()),
              now,
              continueFlowElementTrigger,
              info.variables());

      TimeBucket bucket = TimeBucket.ofMillis(schedule.getNextExecutionTime(now) - now);
      InstanceScheduleKeyDTO scheduledKey =
          new InstanceScheduleKeyDTO(
              processInstance.getProcessInstanceId(),
              pathExtractor.getInstancePath(catchEventInstance),
              catchEventInstance.getFlowNode().getId(),
              bucket);

      catchEventInstance.addScheduledKey(scheduledKey);
      log.info("Forwarding scheduled continuation {}", scheduledKey);
      context.forward(new Record<>(scheduledKey, schedule, now));
    }
  }

  private void forwardScheduledExternalTaskTriggerTimeouts(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstanceDTO processInstance) {
    Queue<ScheduledExternalTaskTriggerTimeoutInfo> scheduledExternalTaskTriggerTimeouts =
        instanceResult.getScheduledExternalTaskTriggerTimeouts();
    while (!scheduledExternalTaskTriggerTimeouts.isEmpty()) {
      ScheduledExternalTaskTriggerTimeoutInfo info = scheduledExternalTaskTriggerTimeouts.poll();

      ExternalTaskInstance<?> externalTaskInstance = info.externalTaskInstance();

      ExternalTaskResponseResultDTO externalTaskResponseResult =
          new ExternalTaskResponseResultDTO(
              ExternalTaskResponseType.TIMEOUT, false, null, null, 0L);
      List<Long> instancePath = pathExtractor.getInstancePath(info.externalTaskInstance());
      ExternalTaskResponseTriggerDTO externalTaskResponseResultDTO =
          new ExternalTaskResponseTriggerDTO(
              processInstance.getProcessInstanceId(),
              instancePath,
              externalTaskResponseResult,
              VariablesDTO.empty());

      TimerEventDefinitionDTO timerEventDefinition = new TimerEventDefinitionDTO();
      String duration = Duration.ofMillis(info.timeoutMs()).toString();
      timerEventDefinition.setTimeDuration(duration);

      long now = clock.millis();
      MessageScheduleDTO schedule =
          messageSchedulerFactory.schedule(
              timerEventDefinition, now, externalTaskResponseResultDTO, VariableScope.empty());

      TimeBucket bucket = TimeBucket.ofMillis(schedule.getNextExecutionTime(now) - now);
      InstanceScheduleKeyDTO scheduleKey =
          new InstanceScheduleKeyDTO(
              processInstance.getProcessInstanceId(),
              instancePath,
              externalTaskInstance.getFlowNode().getId(),
              bucket);

      externalTaskInstance.addScheduledKey(scheduleKey);
      context.forward(new Record<>(scheduleKey, schedule, now));
    }
  }

  private void forwardMessageSubscriptionCommands(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstanceDTO processInstance) {
    Queue<NewCorrelationSubscriptionMessageEventInfo> newCorrelationSubscriptionMessageEventInfos =
        instanceResult.getNewCorrelationSubscriptionMessageEventInfos();
    while (!newCorrelationSubscriptionMessageEventInfos.isEmpty()) {
      NewCorrelationSubscriptionMessageEventInfo messageEvent =
          newCorrelationSubscriptionMessageEventInfos.poll();
      FlowNodeInstance<?> instanceToContinue = messageEvent.elementInstance();
      CorrelationMessageSubscriptionDTO correlationMessageSubscriptionTrigger =
          new CorrelationMessageSubscriptionDTO(
              processInstance.getProcessInstanceId(),
              messageEvent.correlationKey(),
              instanceToContinue != null ? pathExtractor.getInstancePath(instanceToContinue) : null,
              messageEvent.flowNodeToStart() != null
                  ? messageEvent.flowNodeToStart().getId()
                  : null,
              messageEvent.messageName());
      MessageEventKeyDTO messageEventKey =
          correlationMessageSubscriptionTrigger.toMessageEventKey();
      if (instanceToContinue instanceof CatchEventInstance<?> catchEventInstance) {
        catchEventInstance.addMessageSubscriptionWithCorrelationKey(
            messageEventKey, messageEvent.correlationKey());
      }
      context.forward(
          new Record<>(messageEventKey, correlationMessageSubscriptionTrigger, clock.millis()));
    }

    Queue<TerminateCorrelationSubscriptionMessageEventInfo>
        terminateCorrelationSubscriptionMessageEventInfos =
            instanceResult.getTerminateCorrelationSubscriptionMessageEventInfos();
    while (!terminateCorrelationSubscriptionMessageEventInfos.isEmpty()) {
      TerminateCorrelationSubscriptionMessageEventInfo messageEvent =
          terminateCorrelationSubscriptionMessageEventInfos.poll();
      CancelCorrelationMessageSubscriptionDTO terminateSubscriptionTrigger =
          new CancelCorrelationMessageSubscriptionDTO(
              messageEvent.messageName(), messageEvent.correlationKey());
      context.forward(
          new Record<>(
              terminateSubscriptionTrigger.toMessageEventKey(),
              terminateSubscriptionTrigger,
              clock.millis()));
    }
  }

  private void forwardTerminateCommands(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    Queue<TerminateTriggerDTO> newTerminateCommands = instanceResult.getNewTerminateCommands();
    while (!newTerminateCommands.isEmpty()) {
      TerminateTriggerDTO terminateTrigger = newTerminateCommands.poll();
      context.forward(
          new Record<>(terminateTrigger.getProcessInstanceId(), terminateTrigger, clock.millis()));
    }
  }

  private void forwardContinuations(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    Queue<ContinueFlowElementTriggerDTO> continuations = instanceResult.getContinuations();
    while (!continuations.isEmpty()) {
      ContinueFlowElementTriggerDTO continuation = continuations.poll();
      context.forward(
          new Record<>(continuation.getProcessInstanceId(), continuation, clock.millis()));
    }
  }

  private void forwardNewStartCommands(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstanceDTO processInstance) {
    Queue<NewStartCommand> newStartCommands = instanceResult.getNewStartCommands();
    while (!newStartCommands.isEmpty()) {
      NewStartCommand newStartCommand = newStartCommands.poll();

      Set<IoVariableMappingDTO> outputMappings = dtoMapper.toDto(newStartCommand.outputMappings());
      StartCommandDTO startCommand =
          new StartCommandDTO(
              newStartCommand.processInstanceId(),
              processInstance.getProcessInstanceId(),
              null,
              pathExtractor.getInstancePath(newStartCommand.instance()),
              new ProcessDefinitionKey(newStartCommand.calledElement()),
              newStartCommand.variables(),
              newStartCommand.propagateAllToParent(),
              outputMappings);

      context.forward(
          new Record<>(newStartCommand.processInstanceId(), startCommand, clock.millis()));
    }
  }

  private void forwardUserTaskTriggers(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessDefinitionKey definitionKey,
      ProcessInstanceDTO processInstance) {
    Queue<UserTaskInfo> userTasks = instanceResult.getUserTasks();
    while (!userTasks.isEmpty()) {
      UserTaskInfo userTask = userTasks.poll();
      log.info("Forwarding user task {}", userTask);
      UserTaskTriggerDTO userTaskTriggerDTO =
          toUserTaskTrigger(userTask, processInstance.getProcessInstanceId(), definitionKey);
      context.forward(
          new Record<>(processInstance.getProcessInstanceId(), userTaskTriggerDTO, clock.millis()));
    }
  }

  private void forwardExternalTaskRequests(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessDefinitionKey definitionKey,
      ProcessInstanceDTO processInstance) {
    Queue<ExternalTaskInfo> externalTaskRequests = instanceResult.getExternalTaskRequests();
    while (!externalTaskRequests.isEmpty()) {
      ExternalTaskInfo externalTask = externalTaskRequests.poll();
      ExternalTaskTriggerDTO newExternalTaskTrigger =
          toExternalTaskTrigger(
              externalTask, processInstance.getProcessInstanceId(), definitionKey);
      if (externalTask.backoff() == null) {
        // No backoff, forward directly
        log.info("Forwarding external task {}", externalTask);
        context.forward(
            new Record<>(
                newExternalTaskTrigger.getProcessInstanceId(),
                newExternalTaskTrigger,
                clock.millis()));
      } else {
        // Schedule the external task
        long now = clock.millis();

        OneTimeScheduleDTO oneTimeScheduler =
            new OneTimeScheduleDTO(
                newExternalTaskTrigger,
                now,
                Instant.ofEpochMilli(now).plusMillis(externalTask.backoff()).toEpochMilli());

        TimeBucket bucket = TimeBucket.ofMillis(oneTimeScheduler.getNextExecutionTime(now) - now);
        ScheduleKeyDTO scheduledKey =
            new InstanceScheduleKeyDTO(
                processInstance.getProcessInstanceId(),
                pathExtractor.getInstancePath(externalTask.instance()),
                externalTask.element().getId(),
                bucket);
        context.forward(new Record<>(scheduledKey, oneTimeScheduler, now));
      }
    }
  }

  private ExternalTaskTriggerDTO toExternalTaskTrigger(
      ExternalTaskInfo externalTaskInfo,
      UUID processInstanceId,
      ProcessDefinitionKey processDefinitionKey) {
    return new ExternalTaskTriggerDTO(
        processInstanceId,
        processDefinitionKey,
        externalTaskInfo.externalTaskId(),
        pathExtractor.getInstancePath(externalTaskInfo.instance()),
        externalTaskInfo.variables().scopeToDTO());
  }

  private UserTaskTriggerDTO toUserTaskTrigger(
      UserTaskInfo userTaskInfo,
      UUID processInstanceId,
      ProcessDefinitionKey processDefinitionKey) {
    return new UserTaskTriggerDTO(
        processInstanceId,
        processDefinitionKey,
        userTaskInfo.instance().getFlowNode().getId(),
        pathExtractor.getInstancePath(userTaskInfo.instance()),
        userTaskInfo.assignmentDefinition(),
        userTaskInfo.taskSchedule(),
        userTaskInfo.priorityDefinition(),
        userTaskInfo.variables().scopeToDTO());
  }
}

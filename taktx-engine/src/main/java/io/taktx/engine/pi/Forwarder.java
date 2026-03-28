/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.CancelCorrelationMessageSubscriptionDTO;
import io.taktx.dto.CancelInstanceSignalSubscriptionDTO;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.EventSignalDTO;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.NewInstanceSignalSubscriptionDTO;
import io.taktx.dto.OneTimeScheduleDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.TimerEventDefinitionDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.MessageSchedulerFactory;
import io.taktx.engine.pd.model.NewStartCommand;
import io.taktx.engine.pi.model.CancelInstanceSignalSubscriptionInfo;
import io.taktx.engine.pi.model.ExternalTaskInfo;
import io.taktx.engine.pi.model.ExternalTaskInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.NewInstanceSignalSubscriptionInfo;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.ScheduledEventInfo;
import io.taktx.engine.pi.model.ScheduledExternalTaskTriggerTimeoutInfo;
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
      ProcessInstance processInstance) {
    forward(context, instanceResult, definitionKey, processInstance, null, null);
  }

  public void forward(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessDefinitionKey definitionKey,
      ProcessInstance processInstance,
      CommandTrustMetadataDTO currentTrustMetadata,
      CommandTrustMetadataDTO originTrustMetadata) {
    forwardInstanceUpdates(context, instanceResult, currentTrustMetadata, originTrustMetadata);
    forwardExternalTaskRequests(context, instanceResult, definitionKey, processInstance);
    forwardUserTaskTriggers(context, instanceResult, definitionKey, processInstance);
    forwardNewStartCommands(context, instanceResult, processInstance, originTrustMetadata);
    forwardContinuations(context, instanceResult, originTrustMetadata);
    forwardCancelSchedules(context, instanceResult);
    forwardScheduledEvents(context, instanceResult, processInstance, originTrustMetadata);
    forwardScheduledExternalTaskTriggerTimeouts(
        context, instanceResult, processInstance, originTrustMetadata);
    forwardTerminateCommands(context, instanceResult, originTrustMetadata);
    forwardMessageSubscriptionCommands(context, instanceResult, processInstance);
    forwardEventSignalTriggers(context, instanceResult, originTrustMetadata);
    forwardSignals(context, instanceResult);
    forwardSignalSubscriptions(context, instanceResult, processInstance);
  }

  private void forwardSignalSubscriptions(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstance processInstanceDTO) {
    Queue<NewInstanceSignalSubscriptionInfo> newInstanceSignalSubscriptions =
        instanceResult.getNewInstanceSignalSubscriptions();
    while (!newInstanceSignalSubscriptions.isEmpty()) {
      NewInstanceSignalSubscriptionInfo subscriptionInfo = newInstanceSignalSubscriptions.poll();
      NewInstanceSignalSubscriptionDTO subscriptionDTO =
          new NewInstanceSignalSubscriptionDTO(
              processInstanceDTO.getProcessInstanceId(),
              pathExtractor.getInstancePath(subscriptionInfo.elementInstance()),
              subscriptionInfo.name());
      context.forward(new Record<>(subscriptionInfo.name(), subscriptionDTO, clock.millis()));
    }

    Queue<CancelInstanceSignalSubscriptionInfo> cancelInstanceSignalSubscriptions =
        instanceResult.getCancelInstanceSignalSubscriptions();
    while (!cancelInstanceSignalSubscriptions.isEmpty()) {
      CancelInstanceSignalSubscriptionInfo cancelInfo = cancelInstanceSignalSubscriptions.poll();
      CancelInstanceSignalSubscriptionDTO cancelSubscriptionDTO =
          new CancelInstanceSignalSubscriptionDTO(
              processInstanceDTO.getProcessInstanceId(),
              pathExtractor.getInstancePath(cancelInfo.flowNodeInstance()),
              cancelInfo.name());
      context.forward(new Record<>(cancelInfo.name(), cancelSubscriptionDTO, clock.millis()));
    }
  }

  private void forwardSignals(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    Queue<String> eventSignalTriggerList = instanceResult.getSignals();
    while (!eventSignalTriggerList.isEmpty()) {
      String signalName = eventSignalTriggerList.poll();
      SignalDTO signalDTO = new SignalDTO(signalName);
      context.forward(new Record<>(signalDTO.getSignalName(), signalDTO, clock.millis()));
    }
  }

  private void forwardEventSignalTriggers(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      CommandTrustMetadataDTO originTrustMetadata) {
    Queue<EventSignalTriggerDTO> eventSignalTriggerList =
        instanceResult.getEventSignalTriggerList();
    while (!eventSignalTriggerList.isEmpty()) {
      EventSignalTriggerDTO eventSignalTriggerDTO = eventSignalTriggerList.poll();
      applyDerivedCommandTrustMetadata(eventSignalTriggerDTO, originTrustMetadata);
      context.forward(
          new Record<>(
              eventSignalTriggerDTO.getProcessInstanceId(), eventSignalTriggerDTO, clock.millis()));
    }
  }

  private void forwardInstanceUpdates(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      CommandTrustMetadataDTO currentTrustMetadata,
      CommandTrustMetadataDTO originTrustMetadata) {
    Queue<InstanceUpdate> instanceUpdates = instanceResult.getInstanceUpdates();
    if (Boolean.parseBoolean(taktConfiguration.getBroadcastInstanceUpdates())) {
      while (!instanceUpdates.isEmpty()) {
        InstanceUpdate instanceUpdate = instanceUpdates.poll();
        instanceUpdate.update().setCurrentTrustMetadata(currentTrustMetadata);
        instanceUpdate.update().setOriginTrustMetadata(originTrustMetadata);
        InstanceUpdateDTO updateDTO = instanceUpdate.update();
        context.forward(
            new Record<>(instanceUpdate.processInstanceId(), updateDTO, clock.millis()));
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

  private void forwardScheduledEvents(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      CommandTrustMetadataDTO originTrustMetadata) {
    Queue<ScheduledEventInfo> scheduledEventInfos = instanceResult.getScheduledEventInfos();
    while (!scheduledEventInfos.isEmpty()) {
      ScheduledEventInfo info = scheduledEventInfos.poll();

      EventSignalDTO eventDto = dtoMapper.map(info.timerEventSignal());
      IFlowNodeInstance currentInstance = info.timerEventSignal().getCurrentInstance();
      List<Long> elementInstanceIdPath = pathExtractor.getInstancePath(currentInstance);
      eventDto.setElementInstanceIdPath(elementInstanceIdPath);
      EventSignalTriggerDTO eventSignalTriggerDTO =
          new EventSignalTriggerDTO(processInstance.getProcessInstanceId(), eventDto);
      // Timer fires are autonomous engine actions: do NOT carry the scheduling-time origin into the
      // stored schedule. Passing null lets ProcessInstanceProcessor re-assign origin = engine when
      // the timer actually fires, instead of replaying the caller's identity from scheduling time.
      applyDerivedCommandTrustMetadata(eventSignalTriggerDTO, null);

      long now = clock.millis();

      MessageScheduleDTO schedule =
          messageSchedulerFactory.schedule(
              dtoMapper.map(info.timerEventDefinition()),
              now,
              eventSignalTriggerDTO,
              info.variableScope());

      TimeBucket bucket = TimeBucket.ofMillis(schedule.getNextExecutionTime(now) - now);
      InstanceScheduleKeyDTO scheduledKey =
          new InstanceScheduleKeyDTO(
              processInstance.getProcessInstanceId(),
              elementInstanceIdPath,
              info.timerEventSignal().getElementId(),
              bucket);

      info.subscription().setScheduledKey(scheduledKey);
      context.forward(new Record<>(scheduledKey, schedule, now));
    }
  }

  private void forwardScheduledExternalTaskTriggerTimeouts(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      CommandTrustMetadataDTO originTrustMetadata) {
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
      // External task timeouts are autonomous engine actions: do NOT carry the scheduling-time
      // origin. Passing null lets ProcessInstanceProcessor re-assign origin = engine at fire time.
      applyDerivedCommandTrustMetadata(externalTaskResponseResultDTO, null);

      TimerEventDefinitionDTO timerEventDefinition = new TimerEventDefinitionDTO();
      String duration = Duration.ofMillis(info.timeoutMs()).toString();
      timerEventDefinition.setTimeDuration(duration);

      long now = clock.millis();
      MessageScheduleDTO schedule =
          messageSchedulerFactory.schedule(
              timerEventDefinition,
              now,
              externalTaskResponseResultDTO,
              VariableScope.empty(null, null));

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
      ProcessInstance processInstance) {
    Queue<NewCorrelationSubscriptionMessageEventInfo> newCorrelationSubscriptionMessageEventInfos =
        instanceResult.getNewCorrelationSubscriptionMessageEventInfos();
    while (!newCorrelationSubscriptionMessageEventInfos.isEmpty()) {
      NewCorrelationSubscriptionMessageEventInfo messageEvent =
          newCorrelationSubscriptionMessageEventInfos.poll();
      CorrelationMessageSubscriptionDTO correlationMessageSubscriptionTrigger =
          new CorrelationMessageSubscriptionDTO(
              processInstance.getProcessInstanceId(),
              messageEvent.correlationKey(),
              messageEvent.elementInstance() != null
                  ? pathExtractor.getInstancePath(messageEvent.elementInstance())
                  : null,
              messageEvent.flowNodeToStart() != null
                  ? messageEvent.flowNodeToStart().getId()
                  : null,
              messageEvent.messageName());
      MessageEventKeyDTO messageEventKey =
          correlationMessageSubscriptionTrigger.toMessageEventKey();
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
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      CommandTrustMetadataDTO originTrustMetadata) {
    Queue<AbortTriggerDTO> newTerminateCommands = instanceResult.getNewTerminateCommands();
    while (!newTerminateCommands.isEmpty()) {
      AbortTriggerDTO terminateTrigger = newTerminateCommands.poll();
      applyDerivedCommandTrustMetadata(terminateTrigger, originTrustMetadata);
      context.forward(
          new Record<>(terminateTrigger.getProcessInstanceId(), terminateTrigger, clock.millis()));
    }
  }

  private void forwardContinuations(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      CommandTrustMetadataDTO originTrustMetadata) {
    Queue<ContinueFlowElementTriggerDTO> continuations = instanceResult.getContinuations();
    while (!continuations.isEmpty()) {
      ContinueFlowElementTriggerDTO continuation = continuations.poll();
      applyDerivedCommandTrustMetadata(continuation, originTrustMetadata);
      context.forward(
          new Record<>(continuation.getProcessInstanceId(), continuation, clock.millis()));
    }
  }

  private void forwardNewStartCommands(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      CommandTrustMetadataDTO originTrustMetadata) {
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
      applyDerivedCommandTrustMetadata(startCommand, originTrustMetadata);

      context.forward(
          new Record<>(newStartCommand.processInstanceId(), startCommand, clock.millis()));
    }
  }

  private void forwardUserTaskTriggers(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessDefinitionKey definitionKey,
      ProcessInstance processInstance) {
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
      ProcessInstance processInstance) {
    Queue<ExternalTaskInfo> externalTaskRequests = instanceResult.getExternalTaskRequests();
    while (!externalTaskRequests.isEmpty()) {
      ExternalTaskInfo externalTask = externalTaskRequests.poll();
      ExternalTaskTriggerDTO newExternalTaskTrigger =
          toExternalTaskTrigger(
              externalTask, processInstance.getProcessInstanceId(), definitionKey);
      if (externalTask.backoff() == null) {
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
        externalTaskInfo.element().getId(),
        pathExtractor.getInstancePath(externalTaskInfo.instance()),
        externalTaskInfo.variables().scopeAndParentsToDto(),
        externalTaskInfo.headers());
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
        userTaskInfo.variables().scopeAndParentsToDto());
  }

  private void applyDerivedCommandTrustMetadata(
      ProcessInstanceTriggerDTO trigger, CommandTrustMetadataDTO originTrustMetadata) {
    if (trigger != null) {
      trigger.setCurrentTrustMetadata(null);
      trigger.setOriginTrustMetadata(originTrustMetadata);
    }
  }
}

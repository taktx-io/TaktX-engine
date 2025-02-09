package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.MessageSchedulerFactory;
import com.flomaestro.engine.pd.model.NewStartCommand;
import com.flomaestro.engine.pi.model.ExternalTaskInfo;
import com.flomaestro.engine.pi.model.ExternalTaskInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceWithScheduleKeys;
import com.flomaestro.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.engine.pi.model.ReceivingMessageInstance;
import com.flomaestro.engine.pi.model.ScheduledContinuationInfo;
import com.flomaestro.engine.pi.model.ScheduledExternalTaskTriggerTimeoutInfo;
import com.flomaestro.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.CancelCorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseResultDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseType;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceScheduleKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.IoVariableMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.OneTimeScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.TimerEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

@ApplicationScoped
@RequiredArgsConstructor
public class Forwarder {
  private final PathExtractor pathExtractor;
  private final MessageSchedulerFactory messageSchedulerFactory;
  private final DtoMapper dtoMapper;
  private final Clock clock;

  public void forward(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessDefinitionKey definitionKey,
      ProcessInstanceDTO processInstanceDTO) {
    forwardInstanceUpdates(context, instanceResult);
    forwardExternalTaskRequests(context, instanceResult, definitionKey, processInstanceDTO);
    forwardNewStartCommands(context, instanceResult, processInstanceDTO);
    forwardContinuations(context, instanceResult);
    forwardCancelSchedules(context, instanceResult);
    forwardScheduledContinuations(context, instanceResult, processInstanceDTO);
    forwardScheduledExternalTaskTriggerTimeouts(context, instanceResult, processInstanceDTO);
    forwardTerminateCommands(context, instanceResult);
    forwardMessageSubscriptionCommands(context, instanceResult, processInstanceDTO);
  }

  private void forwardInstanceUpdates(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {

    Queue<InstanceUpdate> processInstanceUpdates = instanceResult.getInstanceUpdates();
    while (!processInstanceUpdates.isEmpty()) {
      InstanceUpdate instanceUpdate = processInstanceUpdates.poll();
      context.forward(
          new Record<>(
              instanceUpdate.processInstanceKey(), instanceUpdate.update(), clock.millis()));
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
              processInstance.getProcessInstanceKey(),
              pathExtractor.getInstancePath(catchEventInstance),
              null,
              info.variables().scopeToDTO());

      MessageScheduleDTO schedule =
          messageSchedulerFactory.schedule(
              dtoMapper.map(info.timerEventDefinition()),
              continueFlowElementTrigger,
              info.variables());
      InstanceScheduleKeyDTO scheduledKey =
          new InstanceScheduleKeyDTO(
              processInstance.getProcessInstanceKey(),
              pathExtractor.getInstancePath(catchEventInstance),
              schedule.getTimeBucket(clock.millis()));

      catchEventInstance.addScheduledKey(scheduledKey);
      context.forward(new Record<>(scheduledKey, schedule, clock.millis()));
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
              ExternalTaskResponseType.TIMEOUT, false, null, null, null, 0L);
      ExternalTaskResponseTriggerDTO externalTaskResponseResultDTO =
          new ExternalTaskResponseTriggerDTO(
              processInstance.getProcessInstanceKey(),
              pathExtractor.getInstancePath(info.externalTaskInstance()),
              externalTaskResponseResult,
              VariablesDTO.empty());

      TimerEventDefinitionDTO timerEventDefinition = new TimerEventDefinitionDTO();
      String duration = Duration.ofMillis(info.timeoutMs()).toString();
      timerEventDefinition.setTimeDuration(duration);

      MessageScheduleDTO schedule =
          messageSchedulerFactory.schedule(
              timerEventDefinition, externalTaskResponseResultDTO, VariableScope.empty());
      InstanceScheduleKeyDTO scheduleKey =
          new InstanceScheduleKeyDTO(
              processInstance.getProcessInstanceKey(),
              pathExtractor.getInstancePath(externalTaskInstance),
              schedule.getTimeBucket(clock.millis()));

      externalTaskInstance.addScheduledKey(scheduleKey);
      context.forward(new Record<>(scheduleKey, schedule, clock.millis()));
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
      ReceivingMessageInstance instance = messageEvent.instance();
      CorrelationMessageSubscriptionDTO correlationMessageSubscriptionTrigger =
          new CorrelationMessageSubscriptionDTO(
              processInstance.getProcessInstanceKey(),
              messageEvent.correlationKey(),
              pathExtractor.getInstancePath(instance),
              messageEvent.messageName());
      MessageEventKeyDTO messageEventKey =
          correlationMessageSubscriptionTrigger.toMessageEventKey();
      instance.addMessageSubscriptionWithCorrelationKey(
          messageEventKey, messageEvent.correlationKey());
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
    Queue<UUID> newTerminateCommands = instanceResult.getNewTerminateCommands();
    while (!newTerminateCommands.isEmpty()) {
      UUID processInstanceKey = newTerminateCommands.poll();
      TerminateTriggerDTO terminateTrigger = new TerminateTriggerDTO(processInstanceKey, List.of());
      context.forward(new Record<>(processInstanceKey, terminateTrigger, clock.millis()));
    }
  }

  private void forwardContinuations(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    Queue<ContinueFlowElementTriggerDTO> continuations = instanceResult.getContinuations();
    while (!continuations.isEmpty()) {
      ContinueFlowElementTriggerDTO continuation = continuations.poll();
      context.forward(
          new Record<>(continuation.getProcessInstanceKey(), continuation, clock.millis()));
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
              newStartCommand.processInstanceKey(),
              processInstance.getProcessInstanceKey(),
              null,
              pathExtractor.getInstancePath(newStartCommand.instance()),
              new ProcessDefinitionKey(newStartCommand.calledElement()),
              newStartCommand.variables(),
              newStartCommand.propagateAllToParent(),
              outputMappings);

      context.forward(
          new Record<>(newStartCommand.processInstanceKey(), startCommand, clock.millis()));
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
          toTrigger(externalTask, processInstance.getProcessInstanceKey(), definitionKey);
      if (externalTask.startTime() == null) {
        // No schedule time, forward directly
        context.forward(
            new Record<>(
                newExternalTaskTrigger.getProcessInstanceKey(),
                newExternalTaskTrigger,
                clock.millis()));
      } else {
        // Schedule the external task
        OneTimeScheduleDTO oneTimeScheduler =
            new OneTimeScheduleDTO(newExternalTaskTrigger, Instant.parse(externalTask.startTime()).toEpochMilli());
        ScheduleKeyDTO scheduledKey =
            new InstanceScheduleKeyDTO(
                processInstance.getProcessInstanceKey(),
                pathExtractor.getInstancePath(externalTask.instance()),
                oneTimeScheduler.getTimeBucket(clock.millis()));
        context.forward(new Record<>(scheduledKey, oneTimeScheduler, clock.millis()));
      }
    }
  }

  private ExternalTaskTriggerDTO toTrigger(
      ExternalTaskInfo externalTaskInfo,
      UUID processInstanceKey,
      ProcessDefinitionKey processDefinitionKey) {
    return new ExternalTaskTriggerDTO(
        processInstanceKey,
        processDefinitionKey,
        externalTaskInfo.externalTaskId(),
        pathExtractor.getInstancePath(externalTaskInfo.instance()),
        externalTaskInfo.variables().scopeToDTO());
  }
}

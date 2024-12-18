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
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.CancelCorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseResultDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseType;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceScheduleKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.OneTimeSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.TimerEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

@ApplicationScoped
@RequiredArgsConstructor
public class Forwarder {
  private final VariablesMapper variablesMapper;
  private final PathExtractor pathExtractor;
  private final MessageSchedulerFactory messageSchedulerFactory;
  private final DtoMapper dtoMapper;

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

    Queue<InstanceUpdateDTO> processInstanceUpdates = instanceResult.getProcessInstanceUpdates();
    while (!processInstanceUpdates.isEmpty()) {
      InstanceUpdateDTO processInstanceUpdate = processInstanceUpdates.poll();
      context.forward(
          new Record<>(
              processInstanceUpdate.getProcessInstanceKey(),
              processInstanceUpdate,
              Instant.now().toEpochMilli()));
    }
  }

  private void forwardCancelSchedules(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {

    Queue<ScheduleKeyDTO> cancelSchedules = instanceResult.getCancelSchedules();
    while (!cancelSchedules.isEmpty()) {
      ScheduleKeyDTO scheduledKey = cancelSchedules.poll();
      context.forward(new Record<>(scheduledKey, null, Instant.now().toEpochMilli()));
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
              Constants.NONE,
              variablesMapper.toDTO(info.variables()));

      InstanceScheduleKeyDTO scheduledKey =
          new InstanceScheduleKeyDTO(
              processInstance.getProcessInstanceKey(), catchEventInstance.getElementInstanceId());
      MessageSchedulerDTO schedule =
          messageSchedulerFactory.schedule(
              scheduledKey,
              dtoMapper.map(info.timerEventDefinition()),
              continueFlowElementTrigger,
              info.variables());
      catchEventInstance.addScheduledKey(scheduledKey);
      context.forward(new Record<>(scheduledKey, schedule, Instant.now().toEpochMilli()));
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
              ExternalTaskResponseType.TIMEOUT,
              false,
              Constants.NONE,
              Constants.NONE,
              Constants.NONE,
              0L);
      ExternalTaskResponseTriggerDTO externalTaskResponseResultDTO =
          new ExternalTaskResponseTriggerDTO(
              processInstance.getProcessInstanceKey(),
              pathExtractor.getInstancePath(info.externalTaskInstance()),
              externalTaskResponseResult,
              VariablesDTO.empty());

      TimerEventDefinitionDTO timerEventDefinition = new TimerEventDefinitionDTO();
      String duration = Duration.ofMillis(info.timeoutMs()).toString();
      timerEventDefinition.setTimeDuration(duration);

      InstanceScheduleKeyDTO scheduleKey =
          new InstanceScheduleKeyDTO(
              processInstance.getProcessInstanceKey(), externalTaskInstance.getElementInstanceId());
      MessageSchedulerDTO schedule =
          messageSchedulerFactory.schedule(
              scheduleKey, timerEventDefinition, externalTaskResponseResultDTO, Variables.empty());

      externalTaskInstance.addScheduledKey(scheduleKey);
      context.forward(new Record<>(scheduleKey, schedule, Instant.now().toEpochMilli()));
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
              pathExtractor.getElementIdPath(instance.getFlowNode()),
              pathExtractor.getInstancePath(instance),
              messageEvent.messageName());
      MessageEventKeyDTO messageEventKey =
          correlationMessageSubscriptionTrigger.toMessageEventKey();
      instance.addMessageSubscriptionWithCorrelationKey(
          messageEventKey, messageEvent.correlationKey());
      context.forward(
          new Record<>(
              messageEventKey,
              correlationMessageSubscriptionTrigger,
              Instant.now().toEpochMilli()));
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
              Instant.now().toEpochMilli()));
    }
  }

  private void forwardTerminateCommands(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    Queue<UUID> newTerminateCommands = instanceResult.getNewTerminateCommands();
    while (!newTerminateCommands.isEmpty()) {
      UUID processInstanceKey = newTerminateCommands.poll();
      TerminateTriggerDTO terminateTrigger = new TerminateTriggerDTO(processInstanceKey, List.of());
      context.forward(
          new Record<>(processInstanceKey, terminateTrigger, Instant.now().toEpochMilli()));
    }
  }

  private void forwardContinuations(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    Queue<ContinueFlowElementTriggerDTO> continuations = instanceResult.getContinuations();
    while (!continuations.isEmpty()) {
      ContinueFlowElementTriggerDTO continuation = continuations.poll();
      context.forward(
          new Record<>(
              continuation.getProcessInstanceKey(), continuation, Instant.now().toEpochMilli()));
    }
  }

  private void forwardNewStartCommands(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstanceDTO processInstance) {
    Queue<NewStartCommand> newStartCommands = instanceResult.getNewStartCommands();
    while (!newStartCommands.isEmpty()) {
      NewStartCommand newStartCommand = newStartCommands.poll();
      StartCommandDTO startCommand =
          new StartCommandDTO(
              newStartCommand.processInstanceKey(),
              processInstance.getProcessInstanceKey(),
              Constants.NONE,
              pathExtractor.getElementIdPath(newStartCommand.flowNode()),
              pathExtractor.getInstancePath(newStartCommand.instance()),
              newStartCommand.calledElement(),
              variablesMapper.toDTO(newStartCommand.variables()));

      context.forward(
          new Record<>(
              startCommand.getProcessDefinitionId(), startCommand, Instant.now().toEpochMilli()));
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
                Instant.now().toEpochMilli()));
      } else {
        // Schedule the external task
        ScheduleKeyDTO scheduledKey =
            new InstanceScheduleKeyDTO(
                processInstance.getProcessInstanceKey(),
                externalTask.instance().getElementInstanceId());
        OneTimeSchedulerDTO oneTimeScheduler =
            new OneTimeSchedulerDTO(scheduledKey, newExternalTaskTrigger, externalTask.startTime());
        context.forward(new Record<>(scheduledKey, oneTimeScheduler, Instant.now().toEpochMilli()));
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
        variablesMapper.toDTO(externalTaskInfo.variables()));
  }
}

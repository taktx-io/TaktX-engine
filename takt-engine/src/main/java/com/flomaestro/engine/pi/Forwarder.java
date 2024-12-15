package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.MessageSchedulerFactory;
import com.flomaestro.engine.pd.model.NewStartCommand;
import com.flomaestro.engine.pi.model.CatchEventInstance;
import com.flomaestro.engine.pi.model.ExternalTaskInfo;
import com.flomaestro.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.engine.pi.model.ReceivingMessageInstance;
import com.flomaestro.engine.pi.model.ScheduledContinuationInfo;
import com.flomaestro.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.takt.dto.v_1_0_0.CancelCorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.OneTimeSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduledKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
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
    forwardScheduledContinuations(context, instanceResult, processInstanceDTO);
    forwardCancelSchedules(context, instanceResult);
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

    Queue<ScheduledKeyDTO> cancelSchedules = instanceResult.getCancelSchedules();
    while (!cancelSchedules.isEmpty()) {
      ScheduledKeyDTO scheduledKey = cancelSchedules.poll();
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
      CatchEventInstance<?> catchEventInstance = info.catchEventInstance();
      ContinueFlowElementTriggerDTO continueFlowElementTrigger =
          new ContinueFlowElementTriggerDTO(
              processInstance.getProcessInstanceKey(),
              pathExtractor.getInstancePath(catchEventInstance),
              Constants.NONE,
              variablesMapper.toDTO(info.variables()));

      MessageSchedulerDTO schedule =
          messageSchedulerFactory.schedule(
              processInstance.getProcessDefinitionKey(),
              processInstance.getProcessInstanceKey(),
              catchEventInstance.getFlowNode().getId(),
              dtoMapper.map(info.timerEventDefinition()),
              List.of(continueFlowElementTrigger),
              info.variables());
      catchEventInstance.addScheduledKey(schedule.getScheduledKey());
      context.forward(
          new Record<>(schedule.getScheduledKey(), schedule, Instant.now().toEpochMilli()));
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
        OneTimeSchedulerDTO oneTimeScheduler =
            new OneTimeSchedulerDTO(
                processInstance.getProcessDefinitionKey(),
                processInstance.getProcessInstanceKey(),
                externalTask.element().getId(),
                externalTask.element().getId(),
                List.of(newExternalTaskTrigger),
                externalTask.startTime());
        ScheduledKeyDTO scheduledKey =
            new ScheduledKeyDTO(
                definitionKey,
                processInstance.getProcessInstanceKey(),
                oneTimeScheduler.getScheduleType(),
                externalTask.element().getId(),
                "");
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

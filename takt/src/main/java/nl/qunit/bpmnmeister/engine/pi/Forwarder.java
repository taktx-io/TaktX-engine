package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.MessageSchedulerFactory;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.NewCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ScheduledContinuationInfo;
import nl.qunit.bpmnmeister.pd.model.TerminateCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pi.CancelCorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.InstanceUpdate;
import nl.qunit.bpmnmeister.pi.NewStartCommand;
import nl.qunit.bpmnmeister.pi.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.instances.CatchEventInstance;
import nl.qunit.bpmnmeister.pi.instances.ReceivingMessageInstance;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;
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

    Queue<InstanceUpdate> processInstanceUpdates = instanceResult.getProcessInstanceUpdates();
    while (!processInstanceUpdates.isEmpty()) {
      InstanceUpdate processInstanceUpdate = processInstanceUpdates.poll();
      context.forward(
          new Record<>(
              processInstanceUpdate.getProcessInstanceKey(),
              processInstanceUpdate,
              Instant.now().toEpochMilli()));
    }
  }

  private void forwardCancelSchedules(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {

    Queue<ScheduledKey> cancelSchedules = instanceResult.getCancelSchedules();
    while (!cancelSchedules.isEmpty()) {
      ScheduledKey scheduledKey = cancelSchedules.poll();
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
      ContinueFlowElementTrigger continueFlowElementTrigger =
          new ContinueFlowElementTrigger(
              processInstance.getProcessInstanceKey(),
              pathExtractor.getElementIdPath(catchEventInstance.getFlowNode()),
              pathExtractor.getInstancePath(catchEventInstance),
              Constants.NONE,
              variablesMapper.toDTO(info.variables()));

      MessageScheduler schedule =
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
      CorrelationMessageSubscription correlationMessageSubscriptionTrigger =
          new CorrelationMessageSubscription(
              processInstance.getProcessInstanceKey(),
              messageEvent.correlationKey(),
              pathExtractor.getElementIdPath(instance.getFlowNode()),
              pathExtractor.getInstancePath(instance),
              messageEvent.messageName());
      MessageEventKey messageEventKey = correlationMessageSubscriptionTrigger.toMessageEventKey();
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
      CancelCorrelationMessageSubscription terminateSubscriptionTrigger =
          new CancelCorrelationMessageSubscription(
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
      TerminateTrigger terminateTrigger = new TerminateTrigger(processInstanceKey, List.of());
      context.forward(
          new Record<>(processInstanceKey, terminateTrigger, Instant.now().toEpochMilli()));
    }
  }

  private void forwardContinuations(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    Queue<ContinueFlowElementTrigger> continuations = instanceResult.getContinuations();
    while (!continuations.isEmpty()) {
      ContinueFlowElementTrigger continuation = continuations.poll();
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
      StartCommand startCommand =
          new StartCommand(
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
      ExternalTaskTrigger newExternalTaskTrigger =
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
        OneTimeScheduler oneTimeScheduler =
            new OneTimeScheduler(
                processInstance.getProcessDefinitionKey(),
                processInstance.getProcessInstanceKey(),
                externalTask.element().getId(),
                externalTask.element().getId(),
                List.of(newExternalTaskTrigger),
                externalTask.startTime());
        ScheduledKey scheduledKey =
            new ScheduledKey(
                definitionKey,
                processInstance.getProcessInstanceKey(),
                oneTimeScheduler.getScheduleType(),
                externalTask.element().getId(),
                "");
        context.forward(new Record<>(scheduledKey, oneTimeScheduler, Instant.now().toEpochMilli()));
      }
    }
  }

  private ExternalTaskTrigger toTrigger(
      ExternalTaskInfo externalTaskInfo,
      UUID processInstanceKey,
      ProcessDefinitionKey processDefinitionKey) {
    return new ExternalTaskTrigger(
        processInstanceKey,
        processDefinitionKey,
        pathExtractor.getElementIdPath(externalTaskInfo.element()),
        externalTaskInfo.externalTaskId(),
        pathExtractor.getInstancePath(externalTaskInfo.instance()),
        variablesMapper.toDTO(externalTaskInfo.variables()));
  }
}

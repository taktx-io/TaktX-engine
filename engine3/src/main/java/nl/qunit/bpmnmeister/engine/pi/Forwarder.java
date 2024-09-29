package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.MessageSchedulerFactory;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.CancelCorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance2;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
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
      ProcessInstance2 processInstance) {
    forwardExternalTaskRequests(context, instanceResult, definitionKey, processInstance);
    forwardNewStartCommands(context, instanceResult, processInstance);
    forwardContinuations(context, instanceResult);
    forwardScheduledContinuations(context, instanceResult, processInstance);
    forwardTerminateCommands(context, instanceResult);
    forwardMessageSubscriptionCommands(context, instanceResult, processInstance);
  }

  private void forwardScheduledContinuations(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstance2 processInstance) {
    instanceResult
        .getScheduledContinuationInfos()
        .forEach(
            info -> {
              ContinueFlowElementTrigger2 continueFlowElementTrigger =
                  new ContinueFlowElementTrigger2(
                      processInstance.getProcessInstanceKey(),
                      pathExtractor.getElementIdPath(info.flowNodeInstance().getFlowNode()),
                      pathExtractor.getInstancePath(info.flowNodeInstance()),
                      Constants.NONE,
                      variablesMapper.toDTO(info.variables()));

              MessageScheduler schedule =
                  messageSchedulerFactory.schedule(
                      processInstance.getProcessDefinitionKey(),
                      processInstance.getProcessInstanceKey(),
                      info.flowNodeInstance().getFlowNode().getId(),
                      dtoMapper.map(info.timerEventDefinition()),
                      List.of(continueFlowElementTrigger),
                      info.variables());

              context.forward(
                  new Record<>(schedule.getScheduleKey(), schedule, Instant.now().toEpochMilli()));
            });
  }

  private void forwardMessageSubscriptionCommands(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstance2 processInstance) {
    instanceResult
        .getNewCorrelationSubscriptionMessageEventInfos()
        .forEach(
            messageEvent -> {
              CorrelationMessageSubscription correlationMessageSubscriptionTrigger =
                  new CorrelationMessageSubscription(
                      processInstance.getProcessInstanceKey(),
                      messageEvent.correlationKey(),
                      pathExtractor.getElementIdPath(messageEvent.instance().getFlowNode()),
                      pathExtractor.getInstancePath(messageEvent.instance()),
                      messageEvent.messageName());
              context.forward(
                  new Record<>(
                      correlationMessageSubscriptionTrigger.toMessageEventKey(),
                      correlationMessageSubscriptionTrigger,
                      Instant.now().toEpochMilli()));
            });
    instanceResult
        .getTerminateCorrelationSubscriptionMessageEventInfos()
        .forEach(
            messageEvent -> {
              CancelCorrelationMessageSubscription terminateSubscriptionTrigger =
                  new CancelCorrelationMessageSubscription(
                      messageEvent.messageName(), messageEvent.correlationKey());
              context.forward(
                  new Record<>(
                      terminateSubscriptionTrigger.toMessageEventKey(),
                      terminateSubscriptionTrigger,
                      Instant.now().toEpochMilli()));
            });
  }

  private void forwardTerminateCommands(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    instanceResult
        .getNewTerminateCommands()
        .forEach(
            processInstanceKey -> {
              TerminateTrigger terminateTrigger =
                  new TerminateTrigger(processInstanceKey, List.of());
              context.forward(
                  new Record<>(processInstanceKey, terminateTrigger, Instant.now().toEpochMilli()));
            });
  }

  private void forwardContinuations(
      ProcessorContext<Object, Object> context, InstanceResult instanceResult) {
    instanceResult
        .getContinuations()
        .forEach(
            continuation ->
                context.forward(
                    new Record<>(
                        continuation.getProcessInstanceKey(),
                        continuation,
                        Instant.now().toEpochMilli())));
  }

  private void forwardNewStartCommands(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessInstance2 processInstance) {
    instanceResult
        .getNewStartCommands()
        .forEach(
            newStartCommand -> {
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
                      startCommand.getProcessDefinitionId(),
                      startCommand,
                      Instant.now().toEpochMilli()));
            });
  }

  private void forwardExternalTaskRequests(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessDefinitionKey definitionKey,
      ProcessInstance2 processInstance) {
    instanceResult
        .getExternalTaskRequests()
        .forEach(
            externalTask -> {
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
                ScheduleKey scheduleKey =
                    new ScheduleKey(
                        definitionKey,
                        processInstance.getProcessInstanceKey(),
                        oneTimeScheduler.getScheduleType(),
                        externalTask.element().getId(),
                        "");
                context.forward(
                    new Record<>(scheduleKey, oneTimeScheduler, Instant.now().toEpochMilli()));
              }
            });
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

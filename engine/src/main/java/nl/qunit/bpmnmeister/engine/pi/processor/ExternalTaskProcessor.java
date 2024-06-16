package nl.qunit.bpmnmeister.engine.pi.processor;

import static com.cronutils.utils.StringUtils.isNumeric;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ExternalTask;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FailThrowingEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ExternalTaskState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;

public abstract class ExternalTaskProcessor<T extends ExternalTask, S extends ExternalTaskState>
    extends ActivityProcessor<T, S> {

  @Inject Clock clock;

  @Inject IoMappingProcessor ioMappingProcessor;

  @Override
  protected TriggerResult triggerFlowElementWithoutLoop(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      T element,
      S oldState,
      ScopedVars variables) {
    if (oldState.getState() != FlowNodeStateEnum.READY) {
      return TriggerResult.builder().newFlowNodeState(oldState).build();
    }

    String workerDefinition = getWorkerDefinition(element.getWorkerDefinition(), variables);
    ExternalTaskInfo externalTaskInfo =
        new ExternalTaskInfo(workerDefinition, getExternalTaskVariables(element, variables));
    return TriggerResult.builder()
        .newFlowNodeState(getNewAttempExternalTaskState(oldState))
        .externalTasks(Set.of(externalTaskInfo))
        .build();
  }

  private Variables getExternalTaskVariables(WithIoMapping element, ScopedVars variables) {
    return ioMappingProcessor.getInputVariables(element, variables);
  }

  private String getWorkerDefinition(String workerDefinition, ScopedVars variables) {
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression(workerDefinition, variables);
    return jsonNode.asText();
  }

  @Override
  protected TriggerResult triggerExternalTaskResponse(
      ExternalTaskResponseTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      T element,
      S oldState,
      ScopedVars variables) {

    variables.push(
        new ProcessInstanceKey(UUID.randomUUID()),
        trigger.getProcessInstanceKey(),
        trigger.getVariables());
    Variables mappedVariables = ioMappingProcessor.getOutputVariables(element, variables);
    variables.pop();
    variables.merge(mappedVariables);

    if (oldState.getState() != FlowNodeStateEnum.ACTIVE) {
      return TriggerResult.builder().newFlowNodeState(oldState).build();
    }

    TriggerResult triggerResult =
        getTriggerResultForExternalTaskResponse(
            trigger, processInstance, definition, element, oldState, variables);

    triggerResult =
        getTriggerResultForBoundaryEvents(
            processInstance, definition, element, oldState, triggerResult);
    return triggerResult;
  }

  private TriggerResult getTriggerResultForExternalTaskResponse(
      ExternalTaskResponseTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      T element,
      S oldState,
      ScopedVars variables) {
    if (Boolean.TRUE.equals(trigger.getExternalTaskResponseResult().getSuccess())) {
      return succesfulResponseTriggerResult(
          element, oldState, processInstance, definition, variables);
    } else {
      S newnewExternalTaskState = getNewAttempExternalTaskState(oldState);
      ThrowingEvent throwingEvent = ThrowingEvent.NOOP;
      Set<ExternalTaskInfo> workerDefinitions = Set.of();
      Set<MessageScheduler> messageSchedulers = Set.of();

      if (!element.getRetries().equals(Constants.NONE)) {
        // We have some kind of retry definition
        JsonNode jsonNode =
            feelExpressionHandler.processFeelExpression(element.getRetries(), variables);
        String retryString = jsonNode.asText();

        // Analyze the retry definition
        int retries = -1;
        Optional<String> backoff = Optional.empty();
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
        if (oldState.getAttempt() <= retries
            && Boolean.TRUE.equals(trigger.getExternalTaskResponseResult().getAllowRetry())) {
          // Retry allowed, possibly with backoff
          if (backoff.isPresent()) {
            // This means for now we do nothing, the retry will be scheduled by the scheduler
            MessageScheduler messageScheduler =
                scheduleNextExternalTask(
                    trigger.getElementId(),
                    backoff.get(),
                    processInstance,
                    element.getId(),
                    variables);
            messageSchedulers = Set.of(messageScheduler);
          } else {
            // No backoff time defined, retry directly
            ExternalTaskInfo externalTaskInfo =
                new ExternalTaskInfo(
                    trigger.getElementId(), getExternalTaskVariables(element, variables));
            workerDefinitions = Set.of(externalTaskInfo);
          }
        } else {
          // No more retries, either by limit or by disallowing retry by the worker
          // fail the task and the processInstance
          throwingEvent = new FailThrowingEvent();
          newnewExternalTaskState = getTerminateElementState(oldState);
        }
      } else {
        // No retries allowed, fail the task and the processInstance
        throwingEvent = new FailThrowingEvent();
        newnewExternalTaskState = getTerminateElementState(oldState);
      }

      List<ProcessInstanceTrigger> newProcessInstanceTriggers = new ArrayList<>();
      List<BoundaryEvent> boundaryEvents =
          definition
              .getDefinitions()
              .getRootProcess()
              .getFlowElements()
              .getBoundaryEventsAttachedToElement(element.getId());
      if (elementFinished(oldState, newnewExternalTaskState)) {
        for (BoundaryEvent boundaryEvent : boundaryEvents) {
          newProcessInstanceTriggers.add(
              new TerminateTrigger(processInstance.getProcessInstanceKey(), boundaryEvent.getId()));
        }
      }
      return TriggerResult.builder()
          .newFlowNodeState(newnewExternalTaskState)
          .externalTasks(workerDefinitions)
          .processInstanceTriggers(newProcessInstanceTriggers)
          .throwingEvent(throwingEvent)
          .messageSchedulers(messageSchedulers)
          .build();
    }
  }

  protected abstract S getNewAttempExternalTaskState(S oldState);

  private MessageScheduler scheduleNextExternalTask(
      String workerDefinition,
      String backoff,
      ProcessInstance processInstance,
      String elementId,
      ScopedVars variables) {
    ExternalTaskTrigger externalTask =
        new ExternalTaskTrigger(
            processInstance.getRootInstanceKey(),
            processInstance.getProcessInstanceKey(),
            processInstance.getProcessDefinitionKey(),
            workerDefinition,
            variables.getCurrentScopeVariables());
    String triggerTime = Instant.now(clock).plus(Duration.parse(backoff)).toString();
    return new OneTimeScheduler(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessInstanceKey(),
        elementId,
        workerDefinition,
        List.of(externalTask),
        triggerTime);
  }

  private TriggerResult succesfulResponseTriggerResult(
      T element,
      S oldState,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      ScopedVars variables) {
    return TriggerResult.builder()
        .newFlowNodeState(oldState.getFinishedLoopState())
        .processInstanceTriggers(
            TriggerHelper.getProcessInstanceTriggersForOutputFlows(
                processInstance, processDefinition, element))
        .build();
  }
}

package nl.qunit.bpmnmeister.engine.pi.processor;

import static com.cronutils.utils.StringUtils.isNumeric;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ExternalTaskDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FailThrowingEvent;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import nl.qunit.bpmnmeister.pi.state.ExternalTaskState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;

public abstract class ExternalTaskProcessor<T extends ExternalTaskDTO, S extends ExternalTaskState>
    extends ActivityProcessor<T, S> {

  @Inject Clock clock;

  @Inject IoMappingProcessor ioMappingProcessor;

  @Override
  protected TriggerResult triggerContinueFlowElement(
      ContinueFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      T element,
      S s,
      ScopedVars variables) {
    if (trigger instanceof ExternalTaskResponseTrigger externalTaskResponseTrigger) {
      return triggerExternalTaskResponse(
          externalTaskResponseTrigger, processInstance, definition, element, s, variables);
    } else {
      return TriggerResult.EMPTY;
    }
  }

  @Override
  protected TriggerResult triggerStartFlowElement(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      T element,
      S oldState,
      ScopedVars variables) {
    String externalTaskId = getExternalTaskId(element.getWorkerDefinition(), variables);
    ExternalTaskInfo externalTaskInfo =
        new ExternalTaskInfo(
            externalTaskId,
            element.getId(),
            oldState.getElementInstanceId(),
            getExternalTaskVariables(element, variables));
    return TriggerResult.builder()
        .newFlowNodeStates(List.of(getNewAttempExternalTaskState(oldState)))
        .externalTasks(Set.of(externalTaskInfo))
        .build();
  }

  private VariablesDTO getExternalTaskVariables(WithIoMapping element, ScopedVars variables) {
    return ioMappingProcessor.getInputVariables(element, variables);
  }

  private String getExternalTaskId(String workerDefinition, ScopedVars variables) {
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression(workerDefinition, variables);
    return jsonNode.asText();
  }

  private TriggerResult triggerExternalTaskResponse(
      ExternalTaskResponseTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      T element,
      S oldState,
      ScopedVars variables) {

    variables.push(UUID.randomUUID(), trigger.getProcessInstanceKey(), trigger.getVariables());
    VariablesDTO mappedVariables = ioMappingProcessor.getOutputVariables(element, variables);
    variables.pop();
    variables.merge(mappedVariables);

    if (oldState.getState() != FlowNodeStateEnum.WAITING) {
      return TriggerResult.builder().newFlowNodeStates(List.of(oldState)).build();
    }

    return
        getTriggerResultForExternalTaskResponse(
            trigger, processInstance, definition, element, oldState, variables);

  }

  private TriggerResult getTriggerResultForExternalTaskResponse(
      ExternalTaskResponseTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
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
                    oldState.getElementInstanceId(),
                    variables);
            messageSchedulers = Set.of(messageScheduler);
          } else {
            // No backoff time defined, retry directly
            String externalTaskId = getExternalTaskId(element.getWorkerDefinition(), variables);

            ExternalTaskInfo externalTaskInfo =
                new ExternalTaskInfo(
                    externalTaskId,
                    trigger.getElementId(),
                    oldState.getElementInstanceId(),
                    getExternalTaskVariables(element, variables));
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

      return TriggerResult.builder()
          .newFlowNodeStates(List.of(newnewExternalTaskState))
          .externalTasks(workerDefinitions)
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
      UUID elementInstanceId,
      ScopedVars variables) {
    ExternalTaskTrigger externalTask =
        new ExternalTaskTrigger(
            processInstance.getRootInstanceKey(),
            processInstance.getProcessInstanceKey(),
            processInstance.getProcessDefinitionKey(),
            workerDefinition,
            elementId,
            elementInstanceId,
            variables.getCurrentScopeVariables());
    String triggerTime = Instant.now(clock).plus(Duration.parse(backoff)).toString();
    return new OneTimeScheduler(
        processInstance.getProcessDefinitionKey(),
        processInstance.getRootInstanceKey(),
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
      ProcessDefinitionDTO processDefinition,
      ScopedVars variables) {
    return finishActivity(
        TriggerResult.EMPTY,
        processInstance,
        processDefinition,
        element,
        getFinishedState(oldState),
        variables);
  }

  protected abstract ActivityState getFinishedState(S oldState);
}

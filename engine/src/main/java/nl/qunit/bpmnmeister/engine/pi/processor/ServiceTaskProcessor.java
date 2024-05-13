package nl.qunit.bpmnmeister.engine.pi.processor;

import static com.cronutils.utils.StringUtils.isNumeric;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FailThrowingEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;

@ApplicationScoped
public class ServiceTaskProcessor extends ActivityProcessor<ServiceTask, ServiceTaskState> {

  @Inject FeelExpressionHandler feelExpressionHandler;

  @Inject Clock clock;

  @Override
  protected TriggerResult triggerFlowElementWithoutLoop(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ServiceTask element,
      ServiceTaskState oldState,
      Variables variables) {
    if (oldState.getState() != ActivityStateEnum.READY) {
      return new TriggerResult(
          oldState,
          Set.of(),
          Set.of(),
          Set.of(),
          Set.of(),
          ThrowingEvent.NOOP,
          Set.of(),
          Set.of(),
          Variables.EMPTY);
    }
    String workerDefinition = getWorkerDefinition(element.getWorkerDefinition(), variables);
    return new TriggerResult(
        new ServiceTaskState(
            ActivityStateEnum.ACTIVE,
            oldState.getElementInstanceId(),
            oldState.getPassedCnt(),
            oldState.getLoopCnt(),
            oldState.getAttempt() + 1),
        Set.of(),
        Set.of(workerDefinition),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }

  private String getWorkerDefinition(String workerDefinition, Variables variables) {
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression(workerDefinition, variables);
    return jsonNode.asText();
  }

  @Override
  protected TriggerResult triggerExternalTaskResponse(
      ExternalTaskResponseTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ServiceTask element,
      ServiceTaskState oldState,
      Variables variables) {

    if (oldState.getState() != ActivityStateEnum.ACTIVE) {
      return new TriggerResult(
          oldState,
          Set.of(),
          Set.of(),
          Set.of(),
          Set.of(),
          ThrowingEvent.NOOP,
          Set.of(),
          Set.of(),
          Variables.EMPTY);
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
      ServiceTask element,
      ServiceTaskState oldState,
      Variables variables) {
    if (Boolean.TRUE.equals(trigger.getExternalTaskResponseResult().getSuccess())) {
      return succesfulResponseTriggerResult(trigger, element, oldState);
    } else {
      ServiceTaskState newServiceTaskState =
          new ServiceTaskState(
              ActivityStateEnum.ACTIVE,
              oldState.getElementInstanceId(),
              oldState.getPassedCnt(),
              oldState.getLoopCnt(),
              oldState.getAttempt() + 1);
      ThrowingEvent throwingEvent = ThrowingEvent.NOOP;
      Set<String> workerDefinitions = Set.of();
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
            workerDefinitions = Set.of(trigger.getElementId());
          }
        } else {
          // No more retries, either by limit or by disallowing retry by the worker
          // fail the task and the processInstance
          throwingEvent = new FailThrowingEvent();
          newServiceTaskState =
              new ServiceTaskState(
                  ActivityStateEnum.TERMINATED,
                  oldState.getElementInstanceId(),
                  oldState.getPassedCnt(),
                  oldState.getLoopCnt(),
                  oldState.getAttempt());
        }
      } else {
        // No retries allowed, fail the task and the processInstance
        throwingEvent = new FailThrowingEvent();
        newServiceTaskState =
            new ServiceTaskState(
                ActivityStateEnum.TERMINATED,
                oldState.getElementInstanceId(),
                oldState.getPassedCnt(),
                oldState.getLoopCnt(),
                oldState.getAttempt());
      }

      Set<ProcessInstanceTrigger> newProcessInstanceTriggers = new HashSet<>();
      List<BoundaryEvent> boundaryEvents =
          definition
              .getDefinitions()
              .getRootProcess()
              .getFlowElements()
              .getBoundaryEventsAttachedToElement(element.getId());
      if (elementFinished(oldState, newServiceTaskState)) {
        for (BoundaryEvent boundaryEvent : boundaryEvents) {
          newProcessInstanceTriggers.add(
              new TerminateTrigger(processInstance.getProcessInstanceKey(), boundaryEvent.getId()));
        }
      }

      return new TriggerResult(
          newServiceTaskState,
          Set.of(),
          workerDefinitions,
          newProcessInstanceTriggers,
          Set.of(),
          throwingEvent,
          messageSchedulers,
          Set.of(),
          trigger.getVariables());
    }
  }

  private MessageScheduler scheduleNextExternalTask(
      String workerDefinition,
      String backoff,
      ProcessInstance processInstance,
      String elementId,
      Variables variables) {
    ExternalTaskTrigger externalTask =
        new ExternalTaskTrigger(
            processInstance.getProcessInstanceKey(),
            processInstance.getProcessDefinitionKey(),
            workerDefinition,
            variables);
    String triggerTime = Instant.now(clock).plus(Duration.parse(backoff)).toString();
    return new OneTimeScheduler(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessInstanceKey(),
        elementId,
        workerDefinition,
        List.of(externalTask),
        triggerTime);
  }

  private static TriggerResult succesfulResponseTriggerResult(
      ExternalTaskResponseTrigger trigger, ServiceTask element, ServiceTaskState oldState) {
    return new TriggerResult(
        oldState.getFinishedLoopState(),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Set.of(),
        trigger.getVariables());
  }

  @Override
  protected BpmnElementState getTerminateElementState(ServiceTaskState elementState) {
    ServiceTaskState newState = elementState;
    if (elementState.getState() == ActivityStateEnum.ACTIVE) {
      newState =
          new ServiceTaskState(
              ActivityStateEnum.TERMINATED,
              elementState.getElementInstanceId(),
              elementState.getPassedCnt(),
              elementState.getLoopCnt(),
              elementState.getAttempt());
    }
    return newState;
  }
}

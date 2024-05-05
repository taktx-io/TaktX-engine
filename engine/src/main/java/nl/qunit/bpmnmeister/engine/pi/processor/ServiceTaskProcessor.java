package nl.qunit.bpmnmeister.engine.pi.processor;

import static com.cronutils.utils.StringUtils.isNumeric;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FailThrowingEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;

@ApplicationScoped
public class ServiceTaskProcessor extends ActivityProcessor<ServiceTask, ServiceTaskState> {

  @Inject FeelExpressionHandler feelExpressionHandler;

  @Inject Clock clock;

  @Override
  protected TriggerResult triggerFlowElement(
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
          Variables.EMPTY);
    }
    return new TriggerResult(
        new ServiceTaskState(
            ActivityStateEnum.ACTIVE,
            oldState.getElementInstanceId(),
            oldState.getPassedCnt(),
            oldState.getAttempt() + 1),
        Set.of(),
        Set.of(element.getWorkerDefinition()),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Variables.EMPTY);
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
          Variables.EMPTY);
    }

    if (Boolean.TRUE.equals(trigger.getExternalTaskResponseResult().getSuccess())) {
      return succesfulResponseTriggerResult(trigger, element, oldState);
    } else {
      ServiceTaskState newServiceTaskState =
          new ServiceTaskState(
              ActivityStateEnum.ACTIVE,
              oldState.getElementInstanceId(),
              oldState.getPassedCnt(),
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
                    element.getWorkerDefinition(), backoff.get(), processInstance, variables);
            messageSchedulers = Set.of(messageScheduler);
          } else {
            // No backoff time defined, retry directly
            workerDefinitions = Set.of(element.getWorkerDefinition());
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
                oldState.getAttempt());
      }
      return new TriggerResult(
          newServiceTaskState,
          Set.of(),
          workerDefinitions,
          Set.of(),
          Set.of(),
          throwingEvent,
          messageSchedulers,
          trigger.getVariables());
    }
  }

  private MessageScheduler scheduleNextExternalTask(
      String workerDefinition,
      String backoff,
      ProcessInstance processInstance,
      Variables variables) {
    ExternalTaskTrigger externalTask =
        new ExternalTaskTrigger(
            processInstance.getProcessInstanceKey(),
            processInstance.getProcessDefinitionKey(),
            workerDefinition,
            variables);
    String triggerTime = Instant.now(clock).plus(Duration.parse(backoff)).toString();
    return new OneTimeScheduler(List.of(externalTask), triggerTime);
  }

  private static TriggerResult succesfulResponseTriggerResult(
      ExternalTaskResponseTrigger trigger, ServiceTask element, ServiceTaskState oldState) {
    return new TriggerResult(
        new ServiceTaskState(
            ActivityStateEnum.FINISHED,
            oldState.getElementInstanceId(),
            oldState.getPassedCnt() + 1,
            oldState.getAttempt()),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        trigger.getVariables());
  }

  @Override
  public ServiceTaskState initialState() {
    return new ServiceTaskState(ActivityStateEnum.READY, UUID.randomUUID(), 0, 0);
  }
}

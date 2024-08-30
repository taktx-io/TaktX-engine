package nl.qunit.bpmnmeister.engine.pi.processor.flowelement;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pd.MessageSchedulerFactory;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.engine.pi.processor.CatchEventProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.TriggerHelper;
import nl.qunit.bpmnmeister.pd.model.BoundaryEventDTO;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.MessageDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.state.BoundaryEventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@ApplicationScoped
public class BoundaryEventProcessor extends CatchEventProcessor<BoundaryEventDTO, BoundaryEventState> {
  @Inject MessageSchedulerFactory messageSchedulerFactory;

  @Override
  protected void triggerCatchEventStart(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO processDefinition,
      BoundaryEventDTO element,
      BoundaryEventState oldState,
      ScopedVars variables) {

    Set<MessageEvent> subscriptions =
        getMessageSubscriptions(processInstance, processDefinition, element, oldState, variables);
    Set<MessageEventKey> messageEventKeys =
        subscriptions.stream().map(MessageEvent::getKey).collect(Collectors.toSet());
    Set<MessageScheduler> schedules = getSchedules(processInstance, element, oldState, variables);
    Set<ScheduleKey> scheduleKeys =
        schedules.stream().map(MessageScheduler::getScheduleKey).collect(Collectors.toSet());

    triggerResultBuilder
        .newFlowNodeStates(
            List.of(
                new BoundaryEventState(
                    oldState.getElementInstanceId(),
                    oldState.getElementId(),
                    oldState.getPassedCnt(),
                    FlowNodeStateEnum.WAITING,
                    trigger.getSourceInstanceId(),
                    scheduleKeys,
                    messageEventKeys,
                    oldState.getInputFlowId())))
        .messageSchedulers(schedules)
        .newMessageSubscriptions(subscriptions)
        .build();
  }

  private Set<MessageEvent> getMessageSubscriptions(
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      BoundaryEventDTO element,
      BoundaryEventState oldState,
      ScopedVars variables) {
    return element.getMessageventDefinitions().stream()
        .map(
            messageEventDefinition -> {
              MessageDTO message =
                  definition
                      .getDefinitions()
                      .getMessages()
                      .get(messageEventDefinition.getMessageRef());
              String correlationKeyExpression = message.getCorrelationKey();
              JsonNode jsonNode =
                  feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
              String correlationKey = jsonNode.asText();
              String messageName = message.getName();
              return new CorrelationMessageSubscription(
                  processInstance.getRootInstanceKey(),
                  processInstance.getProcessInstanceKey(),
                  correlationKey,
                  element.getId(),
                  oldState.getElementInstanceId(),
                  messageName);
            })
        .collect(Collectors.toSet());
  }

  @Override
  protected void triggerEventContinue(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      TriggerResultBuilder triggerResultBuilder,
      ProcessInstance processInstance,
      ProcessDefinitionDTO processDefinition,
      BoundaryEventDTO element,
      BoundaryEventState oldState,
      ScopedVars variables) {
    // Trigger by timer or by message.
    List<ProcessInstanceTrigger> processInstanceTriggersForOutputFlows =
        TriggerHelper.getProcessInstanceTriggersForOutputFlows(
            processInstance, processDefinition, oldState, element);
    if (element.isCancelActivity()) {
      // Terminate the activity the boundary event is attached to
      ProcessInstanceTrigger cancelElementTrigger =
          new TerminateTrigger(
              processInstance.getProcessInstanceKey(),
              element.getAttachedToRef(),
              oldState.getAttachedInstanceId());
      List<ProcessInstanceTrigger> elementTriggers = new ArrayList<>();
      elementTriggers.add(cancelElementTrigger);
      elementTriggers.addAll(processInstanceTriggersForOutputFlows);
      triggerResultBuilder
          .newFlowNodeStates(
              List.of(
                  new BoundaryEventState(
                      oldState.getElementInstanceId(),
                      oldState.getElementId(),
                      oldState.getPassedCnt() + 1,
                      FlowNodeStateEnum.FINISHED,
                      oldState.getAttachedInstanceId(),
                      oldState.getScheduleKeys(),
                      oldState.getMessageEventKeys(),
                      oldState.getInputFlowId())))
          .processInstanceTriggers(elementTriggers)
          .cancelSchedules(oldState.getScheduleKeys())
          .cancelMessageSubscriptions(oldState.getMessageEventKeys())
          .build();
    } else {
      // Non interrupting boundary event. Keep the state active and keep listening for messages and
      // timers.
      triggerResultBuilder
          .newFlowNodeStates(
              List.of(
                  new BoundaryEventState(
                      oldState.getElementInstanceId(),
                      oldState.getElementId(),
                      oldState.getPassedCnt() + 1,
                      FlowNodeStateEnum.WAITING,
                      oldState.getAttachedInstanceId(),
                      oldState.getScheduleKeys(),
                      oldState.getMessageEventKeys(),
                      oldState.getInputFlowId())))
          .processInstanceTriggers(processInstanceTriggersForOutputFlows)
          .build();
    }
  }

  private Set<MessageScheduler> getSchedules(
      ProcessInstance processInstance,
      BoundaryEventDTO element,
      BoundaryEventState oldState,
      ScopedVars variables) {
    ContinueFlowElementTrigger timeoutMessage =
        new ContinueFlowElementTrigger(
            processInstance.getProcessInstanceKey(),
            oldState.getElementInstanceId(),
            element.getId(),
            Constants.NONE,
            variables.getCurrentScopeVariables());
    List<SchedulableMessage<?>> timeoutMessages = List.of(timeoutMessage);
    return element.getTimerEventDefinitions().stream()
        .map(
            timerEventDefinition ->
                messageSchedulerFactory.schedule(
                    processInstance.getProcessDefinitionKey(),
                    processInstance.getRootInstanceKey(),
                    processInstance.getProcessInstanceKey(),
                    element.getId(),
                    timerEventDefinition,
                    timeoutMessages,
                    variables))
        .collect(Collectors.toSet());
  }

  @Override
  public TriggerResult terminate(
      TerminateTrigger terminateTrigger,
      BoundaryEventDTO boundaryEvent,
      BoundaryEventState boundaryEventState) {

    return TriggerResult.builder()
        .newFlowNodeStates(List.of(getTerminateElementState(boundaryEventState)))
        .cancelSchedules(boundaryEventState.getScheduleKeys())
        .cancelMessageSubscriptions(boundaryEventState.getMessageEventKeys())
        .build();
  }

  @Override
  protected BoundaryEventState getTerminateElementState(BoundaryEventState elementState) {
    return new BoundaryEventState(
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getAttachedInstanceId(),
        elementState.getScheduleKeys(),
        elementState.getMessageEventKeys(),
        elementState.getInputFlowId());
  }
}

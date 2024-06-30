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
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.Message;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
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
public class BoundaryEventProcessor extends CatchEventProcessor<BoundaryEvent, BoundaryEventState> {
  @Inject MessageSchedulerFactory messageSchedulerFactory;

  @Override
  public TriggerResult terminate(
      TerminateTrigger terminateTrigger,
      BoundaryEvent boundaryEvent,
      BoundaryEventState boundaryEventState) {

    return TriggerResult.builder()
        .newFlowNodeState(
            new BoundaryEventState(
                boundaryEventState.getElementInstanceId(),
                boundaryEventState.getPassedCnt(),
                FlowNodeStateEnum.TERMINATED,
                boundaryEventState.getScheduleKeys(),
                boundaryEventState.getMessageEventKeys(),
                boundaryEventState.getInputFlowId()))
        .cancelSchedules(boundaryEventState.getScheduleKeys())
        .cancelMessageSubscriptions(boundaryEventState.getMessageEventKeys())
        .build();
  }

  @Override
  protected void triggerCatchEvent(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      BoundaryEvent element,
      BoundaryEventState oldState,
      ScopedVars variables) {

    if (oldState.getState() == FlowNodeStateEnum.READY) {
      Set<MessageEvent> subscriptions =
          getMessageSubscriptions(processInstance, processDefinition, element, variables);
      Set<MessageEventKey> messageEventKeys =
          subscriptions.stream().map(MessageEvent::getKey).collect(Collectors.toSet());
      Set<MessageScheduler> schedules =
          getSchedules(triggerResultBuilder, processInstance, element, oldState, variables);
      Set<ScheduleKey> scheduleKeys =
          schedules.stream().map(MessageScheduler::getScheduleKey).collect(Collectors.toSet());

      triggerResultBuilder
          .newFlowNodeState(
              new BoundaryEventState(
                  oldState.getElementInstanceId(),
                  oldState.getPassedCnt(),
                  FlowNodeStateEnum.ACTIVE,
                  scheduleKeys,
                  messageEventKeys,
                  oldState.getInputFlowId()))
          .messageSchedulers(schedules)
          .newMessageSubscriptions(subscriptions)
          .build();
    } else if (oldState.getState() == FlowNodeStateEnum.ACTIVE) {
      trigger(
          triggerResultBuilder, processInstance, processDefinition, element, oldState, variables);
    } else {
      // Any scheduled triggers or messages will be ignored here
      triggerResultBuilder.newFlowNodeState(oldState).build();
    }
  }

  private Set<MessageEvent> getMessageSubscriptions(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      BoundaryEvent element,
      ScopedVars variables) {
    return element.getMessageventDefinitions().stream()
        .map(
            messageEventDefinition -> {
              Message message =
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
                  messageName);
            })
        .collect(Collectors.toSet());
  }

  private void trigger(
      TriggerResultBuilder triggerResultBuilder,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      BoundaryEvent element,
      BoundaryEventState oldState,
      ScopedVars variables) {
    // Trigger by timer or by message.
    List<ProcessInstanceTrigger> processInstanceTriggersForOutputFlows =
        TriggerHelper.getProcessInstanceTriggersForOutputFlows(
            processInstance, processDefinition, element);
    if (element.isCancelActivity()) {
      // Terminate the activity the boundary event is attached to
      ProcessInstanceTrigger cancelElementTrigger =
          new TerminateTrigger(processInstance.getProcessInstanceKey(), element.getAttachedToRef());
      List<ProcessInstanceTrigger> elementTriggers = new ArrayList<>();
      elementTriggers.add(cancelElementTrigger);
      elementTriggers.addAll(processInstanceTriggersForOutputFlows);
      triggerResultBuilder
          .newFlowNodeState(
              new BoundaryEventState(
                  oldState.getElementInstanceId(),
                  oldState.getPassedCnt() + 1,
                  FlowNodeStateEnum.FINISHED,
                  oldState.getScheduleKeys(),
                  oldState.getMessageEventKeys(),
                  oldState.getInputFlowId()))
          .processInstanceTriggers(elementTriggers)
          .cancelSchedules(oldState.getScheduleKeys())
          .cancelMessageSubscriptions(oldState.getMessageEventKeys())
          .build();
    } else {
      // Non interrupting boundary event. Keep the state active and keep listening for messages and
      // timers.
      triggerResultBuilder
          .newFlowNodeState(
              new BoundaryEventState(
                  oldState.getElementInstanceId(),
                  oldState.getPassedCnt() + 1,
                  FlowNodeStateEnum.ACTIVE,
                  oldState.getScheduleKeys(),
                  oldState.getMessageEventKeys(),
                  oldState.getInputFlowId()))
          .processInstanceTriggers(processInstanceTriggersForOutputFlows)
          .build();
    }
  }

  private Set<MessageScheduler> getSchedules(
      TriggerResultBuilder triggerResultBuilder,
      ProcessInstance processInstance,
      BoundaryEvent element,
      BoundaryEventState oldState,
      ScopedVars variables) {
    StartFlowElementTrigger timeoutMessage =
        new StartFlowElementTrigger(
            processInstance.getProcessInstanceKey(),
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
  protected BoundaryEventState getTerminateElementState(BoundaryEventState elementState) {
    return new BoundaryEventState(
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getScheduleKeys(),
        elementState.getMessageEventKeys(),
        elementState.getInputFlowId());
  }
}

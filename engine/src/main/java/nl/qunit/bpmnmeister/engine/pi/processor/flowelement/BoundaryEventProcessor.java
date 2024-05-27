package nl.qunit.bpmnmeister.engine.pi.processor.flowelement;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pd.MessageSchedulerFactory;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.engine.pi.processor.StateProcessor;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.Message;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BoundaryEventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@ApplicationScoped
public class BoundaryEventProcessor extends StateProcessor<BoundaryEvent, BoundaryEventState> {
  @Inject MessageSchedulerFactory messageSchedulerFactory;
  @Inject FeelExpressionHandler feelExpressionHandler;

  @Override
  public TriggerResult terminate(
      ProcessInstance processInstance,
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
  protected TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      BoundaryEvent element,
      BoundaryEventState oldState,
      Variables variables) {
    TriggerResultBuilder triggerResultBuilder = TriggerResult.builder();

    if (oldState.getState() == FlowNodeStateEnum.READY) {
      Set<MessageEvent> subscriptions =
          getMessageSubscriptions(processInstance, definition, element, variables);
      Set<MessageEventKey> messageEventKeys =
          subscriptions.stream().map(MessageEvent::getKey).collect(Collectors.toSet());
      Set<MessageScheduler> schedules =
          getSchedules(triggerResultBuilder, processInstance, element, oldState, variables);
      Set<ScheduleKey> scheduleKeys =
          schedules.stream().map(MessageScheduler::getScheduleKey).collect(Collectors.toSet());

      return TriggerResult.builder()
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
      return trigger(processInstance, element, oldState, variables);
    } else {
      // Any scheduled triggers or messages will be ignored here
      return TriggerResult.builder().newFlowNodeState(oldState).build();
    }
  }

  private Set<MessageEvent> getMessageSubscriptions(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      BoundaryEvent element,
      Variables variables) {
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
                  processInstance.getProcessInstanceKey(),
                  correlationKey,
                  element.getId(),
                  messageName);
            })
        .collect(Collectors.toSet());
  }

  private TriggerResult trigger(
      ProcessInstance processInstance,
      BoundaryEvent element,
      BoundaryEventState oldState,
      Variables variables) {
    // Trigger by timer or by message.
    Set<ProcessInstanceTrigger> cancelElementTriggers = new HashSet<>();
    if (element.isCancelActivity()) {
      // Terminate the activity the boundary event is attached to
      ProcessInstanceTrigger cancelElementTrigger =
          new TerminateTrigger(processInstance.getProcessInstanceKey(), element.getAttachedToRef());
      cancelElementTriggers.add(cancelElementTrigger);

      return TriggerResult.builder()
          .newFlowNodeState(
              new BoundaryEventState(
                  oldState.getElementInstanceId(),
                  oldState.getPassedCnt() + 1,
                  FlowNodeStateEnum.FINISHED,
                  oldState.getScheduleKeys(),
                  oldState.getMessageEventKeys(),
                  oldState.getInputFlowId()))
          .newActiveFlows(element.getOutgoing())
          .newProcessInstanceTriggers(cancelElementTriggers)
          .variables(variables)
          .cancelSchedules(oldState.getScheduleKeys())
          .cancelMessageSubscriptions(oldState.getMessageEventKeys())
          .build();
    } else {
      // Non interrupting boundary event. Keep the state active and keep listening for messages and
      // timers.
      return TriggerResult.builder()
          .newFlowNodeState(
              new BoundaryEventState(
                  oldState.getElementInstanceId(),
                  oldState.getPassedCnt() + 1,
                  FlowNodeStateEnum.ACTIVE,
                  oldState.getScheduleKeys(),
                  oldState.getMessageEventKeys(),
                  oldState.getInputFlowId()))
          .newActiveFlows(element.getOutgoing())
          .variables(variables)
          .build();
    }
  }

  private Set<MessageScheduler> getSchedules(
      TriggerResultBuilder triggerResultBuilder,
      ProcessInstance processInstance,
      BoundaryEvent element,
      BoundaryEventState oldState,
      Variables variables) {
    FlowElementTrigger timeoutMessage =
        new FlowElementTrigger(
            processInstance.getProcessInstanceKey(), element.getId(), Constants.NONE, variables);
    List<SchedulableMessage<?>> timeoutMessages = List.of(timeoutMessage);
    return element.getTimerEventDefinitions().stream()
        .map(
            timerEventDefinition ->
                messageSchedulerFactory.schedule(
                    processInstance.getProcessDefinitionKey(),
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

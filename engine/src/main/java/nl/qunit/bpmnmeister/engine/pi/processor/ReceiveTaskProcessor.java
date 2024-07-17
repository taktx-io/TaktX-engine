package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.ReceiveTaskState;

@ApplicationScoped
public class ReceiveTaskProcessor extends ActivityProcessor<ReceiveTask, ReceiveTaskState> {

  @Override
  protected TriggerResult triggerStartFlowElementWithoutLoop(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ReceiveTask element,
      ReceiveTaskState oldState,
      ScopedVars variables) {
    return subscribeToMessage(processInstance, definition, element, oldState, variables);
  }

  @Override
  protected TriggerResult triggerContinueFlowElement(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ReceiveTask element,
      ReceiveTaskState receiveTaskState,
      ScopedVars variables) {
    return messageReceived(processInstance, definition, element, receiveTaskState, variables);
  }

  private TriggerResult messageReceived(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ReceiveTask element,
      ReceiveTaskState oldState,
      ScopedVars variables) {

    ReceiveTaskState newState =
        new ReceiveTaskState(
            FlowNodeStateEnum.FINISHED,
            oldState.getParentElementInstanceId(),
            oldState.getElementInstanceId(),
            oldState.getElementId(),
            oldState.getPassedCnt() + 1,
            oldState.getLoopCnt(),
            oldState.getInputFlowId());
    return finishActivity(
        TriggerResult.EMPTY, processInstance, definition, element, newState, variables);
  }

  private TriggerResult subscribeToMessage(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ReceiveTask element,
      ReceiveTaskState oldState,
      ScopedVars variables) {
    String correlationKeyExpression =
        definition.getDefinitions().getMessages().get(element.getMessageRef()).getCorrelationKey();
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
    String correlationKey = jsonNode.asText();
    String messageName =
        definition.getDefinitions().getMessages().get(element.getMessageRef()).getName();
    return TriggerResult.builder()
        .newFlowNodeStates(
            List.of(
                new ReceiveTaskState(
                    FlowNodeStateEnum.ACTIVE,
                    oldState.getParentElementInstanceId(),
                    oldState.getElementInstanceId(),
                    oldState.getElementId(),
                    oldState.getPassedCnt(),
                    oldState.getLoopCnt(),
                    oldState.getInputFlowId())))
        .newMessageSubscriptions(
            Set.of(
                new CorrelationMessageSubscription(
                    processInstance.getRootInstanceKey(),
                    processInstance.getProcessInstanceKey(),
                    correlationKey,
                    element.getId(),
                    oldState.getElementInstanceId(),
                    messageName)))
        .build();
  }

  @Override
  protected ReceiveTaskState getTerminateElementState(ReceiveTaskState elementState) {
    return new ReceiveTaskState(
        FlowNodeStateEnum.TERMINATED,
        elementState.getParentElementInstanceId(),
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        elementState.getLoopCnt(),
        elementState.getInputFlowId());
  }
}

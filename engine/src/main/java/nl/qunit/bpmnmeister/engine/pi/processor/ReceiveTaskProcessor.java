package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.ReceiveTaskState;

@ApplicationScoped
public class ReceiveTaskProcessor extends ActivityProcessor<ReceiveTask, ReceiveTaskState> {

  @Override
  protected TriggerResult triggerFlowElementWithoutLoop(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ReceiveTask element,
      ReceiveTaskState oldState,
      Variables variables) {
    if (oldState.getState() == FlowNodeStateEnum.READY) {
      return subscribeToMessage(processInstance, definition, element, oldState, variables);
    } else if (oldState.getState() == FlowNodeStateEnum.ACTIVE) {
      return messageReceived(processInstance, element, oldState, variables);
    }
    return TriggerResult.builder().newFlowNodeState(oldState).build();
  }

  private TriggerResult messageReceived(
      ProcessInstance processInstance,
      ReceiveTask element,
      ReceiveTaskState oldState,
      Variables variables) {

    ReceiveTaskState newState =
        new ReceiveTaskState(
            FlowNodeStateEnum.FINISHED,
            oldState.getElementInstanceId(),
            oldState.getPassedCnt() + 1,
            oldState.getLoopCnt(),
            oldState.getInputFlowId());
    return finishActivity(processInstance, element, newState, variables);
  }

  private TriggerResult subscribeToMessage(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ReceiveTask element,
      ReceiveTaskState oldState,
      Variables variables) {
    String correlationKeyExpression =
        definition.getDefinitions().getMessages().get(element.getMessageRef()).getCorrelationKey();
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
    String correlationKey = jsonNode.asText();
    String messageName =
        definition.getDefinitions().getMessages().get(element.getMessageRef()).getName();
    return TriggerResult.builder()
        .newFlowNodeState(
            new ReceiveTaskState(
                FlowNodeStateEnum.ACTIVE,
                oldState.getElementInstanceId(),
                oldState.getPassedCnt(),
                oldState.getLoopCnt(),
                oldState.getInputFlowId()))
        .newMessageSubscriptions(
            Set.of(
                new CorrelationMessageSubscription(
                    processInstance.getProcessInstanceKey(),
                    correlationKey,
                    element.getId(),
                    messageName)))
        .build();
  }

  @Override
  protected ReceiveTaskState getTerminateElementState(ReceiveTaskState elementState) {
    return new ReceiveTaskState(
        FlowNodeStateEnum.TERMINATED,
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        elementState.getLoopCnt(),
        elementState.getInputFlowId());
  }
}

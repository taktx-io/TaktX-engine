package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowCondition;
import nl.qunit.bpmnmeister.pd.model.Gateway;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.GatewayState;

public abstract class GatewayProcessor<G extends Gateway<S>, S extends GatewayState>
    extends StateProcessor<G, S> {

  @Override
  protected TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      G element,
      S oldState,
      Variables variables) {
    if (oldState.getState() == FlowNodeStateEnum.READY
        || oldState.getState() == FlowNodeStateEnum.ACTIVE) {
      return triggerDecision(trigger, processInstance, definition, element, oldState, variables);
    }
    return TriggerResult.builder().newFlowNodeState(oldState).build();
  }

  protected abstract TriggerResult triggerDecision(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      G element,
      S oldState,
      Variables variables);

  protected Set<String> getOutgoingFlowsMatchingConditionOrDefault(
      ProcessDefinition definition,
      Gateway element,
      Variables variables,
      FeelExpressionHandler feelExpressionHandler) {
    List<SequenceFlow> sequenceFlows =
        definition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getOutgoingSequenceFlowsForElement(element);
    Set<String> flowsWithCondition =
        sequenceFlows.stream()
            .filter(sequenceFlow -> !FlowCondition.NONE.equals(sequenceFlow.getCondition()))
            .filter(
                sequenceFlow ->
                    feelExpressionHandler
                        .processFeelExpression(
                            sequenceFlow.getCondition().getExpression(), variables)
                        .asBoolean())
            .map(BaseElement::getId)
            .collect(Collectors.toSet());
    Set<String> outgoingFlows = new HashSet<>(flowsWithCondition);

    if (outgoingFlows.isEmpty() && !Constants.NONE.equals(element.getDefaultFlow())) {
      outgoingFlows.add(element.getDefaultFlow());
    } else if (sequenceFlows.size() == 1
        && FlowCondition.NONE.equals(sequenceFlows.iterator().next().getCondition())) {
      // Last chance, if no condition is met and no default flow is set, take the only outgoing flow
      // but only if there is no condition on the flow
      outgoingFlows.add(sequenceFlows.iterator().next().getId());
    }
    if (outgoingFlows.isEmpty()) {
      throw new IllegalStateException(
          "No outgoing flow could be selected found for exclusive gateway: " + element.getId());
    }
    return outgoingFlows;
  }
}

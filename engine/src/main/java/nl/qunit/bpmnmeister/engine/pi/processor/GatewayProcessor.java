package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowCondition;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.Gateway;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.GatewayState;

@Slf4j
public abstract class GatewayProcessor<G extends Gateway<S>, S extends GatewayState>
    extends StateProcessor<G, S> {

  @Inject FeelExpressionHandler feelExpressionHandler;

  public final TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      FlowNode<?> element,
      ScopedVars variables) {
    log.info("Trigger processor: " + this);

    if (trigger instanceof StartFlowElementTrigger flowElementTrigger) {
      Optional<FlowNodeState> optFlowNodeState =
          processInstance.getFlowNodeStates().get(flowElementTrigger.getElementId());
      FlowNodeState flowNodeState =
          optFlowNodeState.orElse(
              ((Gateway) element)
                  .getInitialState(
                      flowElementTrigger.getElementId(), flowElementTrigger.getInputFlowId(), 0));

      return triggerStartFlowElement(
          flowElementTrigger,
          processInstance,
          definition,
          (G) element,
          (S) flowNodeState,
          variables);
    } else if (trigger instanceof TerminateTrigger terminateTrigger) {
      Optional<FlowNodeState> flowNodeState =
          processInstance.getFlowNodeStates().get(terminateTrigger.getElementInstanceId());
      if (flowNodeState.isPresent() && flowNodeState.get().getState() == FlowNodeStateEnum.ACTIVE) {
        return terminate(terminateTrigger, (G) element, (S) flowNodeState.get());
      } else {
        return TriggerResult.EMPTY;
      }
    } else {
      throw new IllegalStateException("Unknown trigger type: " + trigger);
    }
  }

  public TriggerResult terminate(TerminateTrigger terminateTrigger, G flowElement, S elementState) {
    return TriggerResult.builder()
        .newFlowNodeStates(List.of(getTerminateElementState(elementState)))
        .build();
  }

  protected abstract S getTerminateElementState(S elementState);

  protected TriggerResult triggerStartFlowElement(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      G element,
      S oldState,
      ScopedVars variables) {
    if (oldState.getState() == FlowNodeStateEnum.READY
        || oldState.getState() == FlowNodeStateEnum.ACTIVE) {
      return triggerDecision(trigger, processInstance, definition, element, oldState, variables);
    }
    return TriggerResult.builder().newFlowNodeStates(List.of(oldState)).build();
  }

  protected abstract TriggerResult triggerDecision(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      G element,
      S oldState,
      ScopedVars variables);

  protected Set<String> getOutgoingFlowsMatchingConditionOrDefault(
      ProcessDefinition definition,
      Gateway element,
      ScopedVars variables,
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

  protected List<ProcessInstanceTrigger> getProcessInstanceTriggers(
      ProcessDefinition definition,
      ProcessInstance processInstance,
      Gateway element,
      GatewayState oldState,
      ScopedVars variables) {
    Set<String> outgoingFlows =
        getOutgoingFlowsMatchingConditionOrDefault(
            definition, element, variables, feelExpressionHandler);
    return outgoingFlows.stream()
        .map(
            flowId -> {
              Optional<FlowNode<?>> flowNodeWithIncomingFlow =
                  definition
                      .getDefinitions()
                      .getRootProcess()
                      .getFlowElements()
                      .getFlowNodeWithIncomingFlow(flowId);
              return new StartFlowElementTrigger(
                  processInstance.getProcessInstanceKey(),
                  oldState.getElementInstanceId(),
                  flowNodeWithIncomingFlow.get().getId(),
                  flowId,
                  variables.getCurrentScopeVariables());
            })
        .collect(Collectors.toList());
  }
}

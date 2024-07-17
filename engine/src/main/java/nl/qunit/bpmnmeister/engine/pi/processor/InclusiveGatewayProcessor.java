package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.InclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.InclusiveGatewayState;

@ApplicationScoped
public class InclusiveGatewayProcessor
    extends GatewayProcessor<InclusiveGateway, InclusiveGatewayState> {

  @Override
  protected TriggerResult triggerDecision(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      InclusiveGateway element,
      InclusiveGatewayState oldState,
      ScopedVars variables) {

    Set<String> incomingFlows = element.getIncoming();
    Set<String> triggeredInputFlows = new HashSet<>(oldState.getTriggeredInputFlows());
    triggeredInputFlows.add(trigger.getInputFlowId());
    Set<String> remainingIncomingFlows = new HashSet<>(incomingFlows);
    remainingIncomingFlows.removeAll(triggeredInputFlows);

    if (canTriggerOutputFlows(remainingIncomingFlows, definition, processInstance)) {
      Set<String> newActiveFlows =
          getOutgoingFlowsMatchingConditionOrDefault(
              definition, element, variables, feelExpressionHandler);
      return TriggerResult.builder()
          .newFlowNodeStates(
              List.of(
                  new InclusiveGatewayState(
                      oldState.getElementInstanceId(),
                      oldState.getElementId(),
                      oldState.getPassedCnt() + 1,
                      FlowNodeStateEnum.FINISHED,
                      oldState.getInputFlowId(),
                      triggeredInputFlows,
                      newActiveFlows)))
          .processInstanceTriggers(
              getProcessInstanceTriggers(definition, processInstance, element, oldState, variables))
          .build();

    } else {
      // Not all incoming flows have been triggered, so we will not trigger the output flows
      return TriggerResult.builder()
          .newFlowNodeStates(
              List.of(
                  new InclusiveGatewayState(
                      oldState.getElementInstanceId(),
                      oldState.getElementId(),
                      oldState.getPassedCnt(),
                      FlowNodeStateEnum.ACTIVE,
                      oldState.getInputFlowId(),
                      triggeredInputFlows,
                      oldState.getSelectedOutputFlows())))
          .build();
    }
  }

  private boolean canTriggerOutputFlows(
      Set<String> remainingIncomingFlows,
      ProcessDefinition definition,
      ProcessInstance processInstance) {
    // Collect all Inclusive gateway for each incoming flow for this current flowNode.

    Map<String, Set<GatewayOutgoingFlowPair>> inclusiveGateways =
        findPreviousInclusiveGatewaysForFlows(definition, remainingIncomingFlows);

    boolean allIncomingFlowsTriggered = true;
    // For each incoming flow check if the corresponding output flows have been triggered,
    // if any of them hasnt, we will not trigger the output flows
    // If there are no corresponding gateways, we assume we are a diverging and allow the flow to
    // trigger the output flows.
    for (String incomingFlow : remainingIncomingFlows) {
      Set<GatewayOutgoingFlowPair> pairs =
          inclusiveGateways.getOrDefault(incomingFlow, new HashSet<>());
      for (GatewayOutgoingFlowPair pair : pairs) {
        Optional<FlowNodeState> flowNodeState =
            processInstance.getFlowNodeStates().get(pair.inclusiveGateway.getId());
        if (flowNodeState.isPresent()) {
          InclusiveGatewayState inclusiveGatewayState = (InclusiveGatewayState) flowNodeState.get();
          if (inclusiveGatewayState.getSelectedOutputFlows().contains(pair.outputFlowId)) {
            allIncomingFlowsTriggered = false;
            break;
          }
        } else {
          allIncomingFlowsTriggered =
              canTriggerOutputFlows(
                  pair.inclusiveGateway.getIncoming(), definition, processInstance);
          break;
        }
      }
      if (!allIncomingFlowsTriggered) {
        break;
      }
    }
    return allIncomingFlowsTriggered;
  }

  private Map<String, Set<GatewayOutgoingFlowPair>> findPreviousInclusiveGatewaysForFlows(
      ProcessDefinition definition, Set<String> incomingFlows) {
    Map<String, Set<GatewayOutgoingFlowPair>> inclusiveGateways = new HashMap<>();
    for (String incomingFlow : incomingFlows) {
      Set<GatewayOutgoingFlowPair> previousInclusiveGateway =
          findPreviousInclusiveGatewayState(incomingFlow, definition);
      Set<GatewayOutgoingFlowPair> inclusiveGatewaySet =
          inclusiveGateways.computeIfAbsent(incomingFlow, k -> new HashSet<>());
      if (inclusiveGatewaySet.containsAll(previousInclusiveGateway)) {
        // Duplicates, so we must be running in circles
        continue;
      }
      inclusiveGatewaySet.addAll(previousInclusiveGateway);
    }
    return inclusiveGateways;
  }

  private Set<GatewayOutgoingFlowPair> findPreviousInclusiveGatewayState(
      String flowId, ProcessDefinition definition) {
    Set<GatewayOutgoingFlowPair> inclusiveGateways = new HashSet<>();
    Optional<FlowNode<?>> optElementWithOutgoingFlow =
        definition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getFlowNodeWithOutgoingFlow(flowId);
    if (optElementWithOutgoingFlow.isPresent()) {
      FlowNode<?> flowNode = optElementWithOutgoingFlow.get();
      if (flowNode instanceof InclusiveGateway inclusiveGateway) {
        inclusiveGateways.add(new GatewayOutgoingFlowPair(inclusiveGateway, flowId));
      } else {
        // Not an inclusive gateway, so we need to look further upstream
        Map<String, Set<GatewayOutgoingFlowPair>> previousInclusiveGatewaysForFlows =
            findPreviousInclusiveGatewaysForFlows(definition, flowNode.getIncoming());
        Collection<Set<GatewayOutgoingFlowPair>> values =
            previousInclusiveGatewaysForFlows.values();
        for (Set<GatewayOutgoingFlowPair> value : values) {
          inclusiveGateways.addAll(value);
        }
      }
    }
    return inclusiveGateways;
  }

  @Override
  protected InclusiveGatewayState getTerminateElementState(InclusiveGatewayState elementState) {
    return new InclusiveGatewayState(
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId(),
        elementState.getTriggeredInputFlows(),
        elementState.getSelectedOutputFlows());
  }

  private record GatewayOutgoingFlowPair(InclusiveGateway inclusiveGateway, String outputFlowId) {}
}

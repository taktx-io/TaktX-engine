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
import nl.qunit.bpmnmeister.pd.model.FlowNodeDTO;
import nl.qunit.bpmnmeister.pd.model.InclusiveGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.InclusiveGatewayState;

@ApplicationScoped
public class InclusiveGatewayProcessor
    extends GatewayProcessor<InclusiveGatewayDTO, InclusiveGatewayState> {

  @Override
  protected TriggerResult triggerDecision(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      InclusiveGatewayDTO element,
      InclusiveGatewayState oldState,
      ScopedVars variables) {

    Set<String> incomingFlows = element.getIncoming();
    Set<String> triggeredInputFlows = new HashSet<>(oldState.getTriggeredInputFlows());
    triggeredInputFlows.add(trigger.getInputFlowId());

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
                      FlowNodeStateEnum.WAITING,
                      oldState.getInputFlowId(),
                      triggeredInputFlows,
                      oldState.getSelectedOutputFlows())))
          .build();
    }
  }

  private boolean canTriggerOutputFlows(
      Set<String> remainingIncomingFlows,
      ProcessDefinitionDTO definition,
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
        List<FlowNodeStateDTO> flowNodeStates =
            processInstance.getFlowNodeStates().get(pair.inclusiveGateway.getId());
        if (!flowNodeStates.isEmpty()) {
          InclusiveGatewayState inclusiveGatewayState =
              (InclusiveGatewayState) flowNodeStates.get(0);
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
      ProcessDefinitionDTO definition, Set<String> incomingFlows) {
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
      String flowId, ProcessDefinitionDTO definition) {
    Set<GatewayOutgoingFlowPair> inclusiveGateways = new HashSet<>();
    Optional<FlowNodeDTO> optElementWithOutgoingFlow =
        definition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getFlowNodeWithOutgoingFlow(flowId);
    if (optElementWithOutgoingFlow.isPresent()) {
      FlowNodeDTO flowNode = optElementWithOutgoingFlow.get();
      if (flowNode instanceof InclusiveGatewayDTO inclusiveGateway) {
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

  private record GatewayOutgoingFlowPair(InclusiveGatewayDTO inclusiveGateway, String outputFlowId) {}
}

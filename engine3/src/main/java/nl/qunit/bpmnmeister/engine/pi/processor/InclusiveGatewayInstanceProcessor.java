package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.InclusiveGateway2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow2;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.InclusiveGatewayInstance;

@ApplicationScoped
@NoArgsConstructor
public class InclusiveGatewayInstanceProcessor
    extends GatewayInstanceProcessor<
        InclusiveGateway2, InclusiveGatewayInstance, ContinueFlowElementTrigger2> {

  @Inject
  public InclusiveGatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, feelExpressionHandler, variablesMapper);
  }

  @Override
  protected InstanceResult processStartSpecificGatewayInstance(
      FlowElements2 flowElements,
      InclusiveGatewayInstance gatewayInstance,
      String inputFlowId,
      Variables2 variables) {
    gatewayInstance.addTriggeredInputFlow(inputFlowId);
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processTerminateSpecificGatewayInstance(
      InclusiveGatewayInstance instance) {
    return InstanceResult.empty();
  }

  @Override
  protected boolean canTriggerOutputFlows(
      InclusiveGatewayInstance gatewayInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates) {
    // For each incoming flow check if the corresponding output flows have been triggered,
    // if any of them hasnt, we will not trigger the output flows
    // If there are no corresponding gateways, we assume we are a diverging and allow the flow to
    // trigger the output flows.

    Map<SequenceFlow2, Set<InclusiveGatewayInstance>> previousTriggeredInstancePairs =
        findPreviousInclusiveGatewayInstances(
            gatewayInstance.getFlowNode().getIncomingSequenceFlows(), flowNodeStates);
    Set<String> collect =
        previousTriggeredInstancePairs.keySet().stream()
            .map(SequenceFlow2::getId)
            .collect(Collectors.toSet());
    if (previousTriggeredInstancePairs.isEmpty()) {
      return true;
    } else return collect.containsAll(gatewayInstance.getTriggeredInputFlows());
  }

  private Map<SequenceFlow2, Set<InclusiveGatewayInstance>> findPreviousInclusiveGatewayInstances(
      Set<SequenceFlow2> incomingSequenceFlows, FlowNodeStates2 flowNodeStates) {
    Map<SequenceFlow2, Set<InclusiveGatewayInstance>> instanceMap = new HashMap<>();
    for (SequenceFlow2 incomingSequenceFlow : incomingSequenceFlows) {
      FlowNode2 sourceNode = incomingSequenceFlow.getSourceNode();
      if (sourceNode instanceof InclusiveGateway2 inclusiveGateway) {
        Optional<FLowNodeInstance> instanceWithFlowNode =
            flowNodeStates.getInstanceWithFlowNode(inclusiveGateway);
        if (instanceWithFlowNode.isPresent()) {
          InclusiveGatewayInstance gatewayInstance =
              (InclusiveGatewayInstance) instanceWithFlowNode.get();
          if (gatewayInstance.getSelectedOutputFlows().contains(incomingSequenceFlow.getId())) {
            instanceMap
                .computeIfAbsent(incomingSequenceFlow, k -> new HashSet<>())
                .add(gatewayInstance);
          }
        }
      } else {
        Map<SequenceFlow2, Set<InclusiveGatewayInstance>> previousInclusiveGatewayInstances =
            findPreviousInclusiveGatewayInstances(
                sourceNode.getIncomingSequenceFlows(), flowNodeStates);
        if (!previousInclusiveGatewayInstances.isEmpty()) {
          instanceMap
              .computeIfAbsent(incomingSequenceFlow, k -> new HashSet<>())
              .addAll(
                  previousInclusiveGatewayInstances.values().stream()
                      .flatMap(Set::stream)
                      .toList());
        }
      }
    }
    return instanceMap;
  }
}

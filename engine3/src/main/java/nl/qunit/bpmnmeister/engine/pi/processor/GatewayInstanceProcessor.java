package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowCondition;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.Gateway2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow2;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.GatewayInstance;

@NoArgsConstructor
public abstract class GatewayInstanceProcessor<
        E extends Gateway2, I extends GatewayInstance<E>, C extends ContinueFlowElementTrigger2>
    extends FLowNodeInstanceProcessor<E, I, C> {

  private FeelExpressionHandler feelExpressionHandler;

  protected GatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected final InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements, I gatewayInstance, String inputFlowId, Variables2 variables) {
    return processStartSpecificGatewayInstance(
        flowElements, gatewayInstance, inputFlowId, variables);
  }

  @Override
  protected final InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      I flowNodeInstance,
      C trigger,
      Variables2 processInstanceVariables,
      FlowNodeStates2 flowNodeStates) {
    // Should never happen
    return InstanceResult.empty();
  }

  @Override
  protected Set<SequenceFlow2> getSelectedSequenceFlows(
      I gatewayInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates,
      Variables2 variables) {
    Set<SequenceFlow2> outgoingFlows = new HashSet<>();
    if (canTriggerOutputFlows(gatewayInstance, flowElements, flowNodeStates)) {
      E gatewayNode = gatewayInstance.getFlowNode();
      Set<SequenceFlow2> sequenceFlows = gatewayNode.getOutGoingSequenceFlows();
      Set<SequenceFlow2> flowsWithCondition =
          sequenceFlows.stream()
              .filter(sequenceFlow -> !FlowCondition.NONE.equals(sequenceFlow.getCondition()))
              .filter(
                  sequenceFlow ->
                      feelExpressionHandler
                          .processFeelExpression(
                              sequenceFlow.getCondition().getExpression(), variables)
                          .asBoolean())
              .collect(Collectors.toSet());

      outgoingFlows.addAll(flowsWithCondition);
      if (outgoingFlows.isEmpty() && !Constants.NONE.equals(gatewayNode.getDefaultFlow())) {
        outgoingFlows.add(gatewayNode.getDefaultSequenceFlow());
      } else if (outgoingFlows.isEmpty() && !sequenceFlows.isEmpty()) {
        // Last chance, if no condition is met and no default flow is set, take the outgoing flows
        outgoingFlows.addAll(sequenceFlows);
      }
      if (outgoingFlows.isEmpty()) {
        throw new IllegalStateException(
            "No outgoing flow could be selected found for exclusive gateway: "
                + gatewayNode.getId());
      }
    }
    gatewayInstance.setSelectedOutputFlows(
        outgoingFlows.stream().map(SequenceFlow2::getId).collect(Collectors.toSet()));
    return outgoingFlows;
  }

  protected abstract boolean canTriggerOutputFlows(
      I gatewayInstance, FlowElements2 flowElements, FlowNodeStates2 flowNodeStates);

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(I instance) {
    return processTerminateSpecificGatewayInstance(instance);
  }

  protected abstract InstanceResult processStartSpecificGatewayInstance(
      FlowElements2 flowElements, I flownodeInstance, String inputFlowId, Variables2 variables);

  protected abstract InstanceResult processTerminateSpecificGatewayInstance(I instance);
}

package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceException;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowCondition;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.Gateway;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.GatewayInstance;

@NoArgsConstructor
public abstract class GatewayInstanceProcessor<
        E extends Gateway, I extends GatewayInstance<E>, C extends ContinueFlowElementTrigger>
    extends FLowNodeInstanceProcessor<E, I, C> {

  private FeelExpressionHandler feelExpressionHandler;

  protected GatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected final void processStartSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I gatewayInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables) {
    processStartSpecificGatewayInstance(
        instanceResult,
        directInstanceResult,
        flowElements,
        gatewayInstance,
        inputFlowId,
        variables);
  }

  @Override
  protected final void processContinueSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I flowNodeInstance,
      C trigger,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {
    // Should never happen
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I gatewayInstance,
      FlowNodeInstances flowNodeInstances,
      Variables variables) {
    Set<SequenceFlow> outgoingFlows = new HashSet<>();
    if (canTriggerOutputFlows(gatewayInstance, flowNodeInstances)) {
      gatewayInstance.resetFlows();
      E gatewayNode = gatewayInstance.getFlowNode();
      Set<SequenceFlow> sequenceFlows = gatewayNode.getOutGoingSequenceFlows();
      Set<SequenceFlow> flowsWithCondition =
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
        throw new ProcessInstanceException(
            processInstance,
            gatewayInstance,
            "No outgoing flow could be selected found for exclusive gateway: "
                + gatewayNode.getId());
      }
    }
    gatewayInstance.setSelectedOutputFlows(
        outgoingFlows.stream().map(SequenceFlow::getId).collect(Collectors.toSet()));
    return outgoingFlows;
  }

  protected abstract boolean canTriggerOutputFlows(
      I gatewayInstance, FlowNodeInstances flowNodeInstances);

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      Variables variables) {
    processTerminateSpecificGatewayInstance(instanceResult, directInstanceResult, instance);
  }

  protected abstract void processStartSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      String inputFlowId,
      Variables variables);

  protected abstract void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult, DirectInstanceResult directInstanceResult, I instance);
}

package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway2;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow2;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ParallelGatewayInstance;

@ApplicationScoped
@NoArgsConstructor
public class ParallelGatewayInstanceProcessor
    extends GatewayInstanceProcessor<
        ParallelGateway2, ParallelGatewayInstance, ContinueFlowElementTrigger2> {

  @Inject
  public ParallelGatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, FeelExpressionHandler feelExpressionHandler) {
    super(ioMappingProcessor, feelExpressionHandler);
  }

  @Override
  protected boolean canTriggerOutputFlows(
      ParallelGatewayInstance gatewayInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates) {
    return true;
  }

  @Override
  protected InstanceResult processStartSpecificGatewayInstance(
      FlowElements2 flowElements,
      ParallelGatewayInstance flownodeInstance,
      String inputFlowId,
      Variables2 variables) {
    flownodeInstance.addTriggeredFlow(inputFlowId);
    if (flownodeInstance.getFlowNode().getIncoming().equals(flownodeInstance.getTriggeredFlows())) {
      flownodeInstance.clearTriggeredFlows();
    }
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processTerminateSpecificGatewayInstance(
      ParallelGatewayInstance instance) {
    return InstanceResult.empty();
  }

  @Override
  protected Set<SequenceFlow2> getSelectedSequenceFlows(
      ParallelGatewayInstance flowNodeInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates,
      Variables2 variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }
}

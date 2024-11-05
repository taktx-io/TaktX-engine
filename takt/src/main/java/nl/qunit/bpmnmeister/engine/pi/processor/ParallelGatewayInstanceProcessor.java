package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.ParallelGatewayInstance;

@ApplicationScoped
@NoArgsConstructor
public class ParallelGatewayInstanceProcessor
    extends GatewayInstanceProcessor<
        ParallelGateway, ParallelGatewayInstance, ContinueFlowElementTrigger> {

  @Inject
  public ParallelGatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, feelExpressionHandler, variablesMapper);
  }

  @Override
  protected boolean canTriggerOutputFlows(
      ParallelGatewayInstance gatewayInstance, FlowNodeInstances flowNodeInstances) {
    return true;
  }

  @Override
  protected InstanceResult processStartSpecificGatewayInstance(
      FlowElements flowElements,
      ParallelGatewayInstance flownodeInstance,
      String inputFlowId,
      Variables variables) {
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
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ParallelGatewayInstance flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      Variables variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }
}

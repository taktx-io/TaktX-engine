package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ExclusiveGatewayInstance;

@ApplicationScoped
@NoArgsConstructor
public class ExclusiveGatewayInstanceProcessor
    extends GatewayInstanceProcessor<
        ExclusiveGateway2, ExclusiveGatewayInstance, ContinueFlowElementTrigger2> {

  @Inject
  public ExclusiveGatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, FeelExpressionHandler feelExpressionHandler) {
    super(ioMappingProcessor, feelExpressionHandler);
  }

  @Override
  protected boolean canTriggerOutputFlows(
      ExclusiveGatewayInstance gatewayInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates) {
    return true;
  }

  @Override
  protected InstanceResult processStartSpecificGatewayInstance(
      FlowElements2 flowElements,
      ExclusiveGatewayInstance flownodeInstance,
      String inputFlowId,
      Variables2 variables) {
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processTerminateSpecificGatewayInstance(
      ExclusiveGatewayInstance instance) {
    return InstanceResult.empty();
  }
}

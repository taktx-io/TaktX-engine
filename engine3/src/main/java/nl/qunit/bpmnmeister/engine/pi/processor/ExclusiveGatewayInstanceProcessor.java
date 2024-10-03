package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.ExclusiveGatewayInstance;

@ApplicationScoped
@NoArgsConstructor
public class ExclusiveGatewayInstanceProcessor
    extends GatewayInstanceProcessor<
        ExclusiveGateway, ExclusiveGatewayInstance, ContinueFlowElementTrigger> {

  @Inject
  public ExclusiveGatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, feelExpressionHandler, variablesMapper);
  }

  @Override
  protected boolean canTriggerOutputFlows(
      ExclusiveGatewayInstance gatewayInstance,
      FlowElements flowElements,
      FlowNodeInstances flowNodeInstances) {
    return true;
  }

  @Override
  protected InstanceResult processStartSpecificGatewayInstance(
      FlowElements flowElements,
      ExclusiveGatewayInstance flownodeInstance,
      String inputFlowId,
      Variables variables) {
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processTerminateSpecificGatewayInstance(
      ExclusiveGatewayInstance instance) {
    return InstanceResult.empty();
  }
}

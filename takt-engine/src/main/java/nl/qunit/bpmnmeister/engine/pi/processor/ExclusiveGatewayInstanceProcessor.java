package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
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
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, feelExpressionHandler, processInstanceMapper, variablesMapper);
  }

  @Override
  protected boolean canTriggerOutputFlows(
      ExclusiveGatewayInstance gatewayInstance, FlowNodeInstances flowNodeInstances) {
    return true;
  }

  @Override
  protected void processStartSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      ExclusiveGatewayInstance flownodeInstance,
      String inputFlowId,
      Variables variables) {}

  @Override
  protected void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      ExclusiveGatewayInstance instance) {}
}

package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.engine.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.ExclusiveGatewayInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTriggerDTO;
import nl.qunit.bpmnmeister.pi.Variables;

@ApplicationScoped
@NoArgsConstructor
public class ExclusiveGatewayInstanceProcessor
    extends GatewayInstanceProcessor<
    ExclusiveGateway, ExclusiveGatewayInstance, ContinueFlowElementTriggerDTO> {

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

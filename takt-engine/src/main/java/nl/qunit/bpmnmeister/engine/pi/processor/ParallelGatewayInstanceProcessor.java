package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstances;
import nl.qunit.bpmnmeister.engine.pi.model.ParallelGatewayInstance;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTriggerDTO;
import nl.qunit.bpmnmeister.pi.Variables;

@ApplicationScoped
@NoArgsConstructor
public class ParallelGatewayInstanceProcessor
    extends GatewayInstanceProcessor<
    ParallelGateway, ParallelGatewayInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public ParallelGatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, feelExpressionHandler, processInstanceMapper, variablesMapper);
  }

  @Override
  protected boolean canTriggerOutputFlows(
      ParallelGatewayInstance gatewayInstance, FlowNodeInstances flowNodeInstances) {
    return gatewayInstance
        .getFlowNode()
        .getIncoming()
        .equals(gatewayInstance.getTriggeredInputFlows());
  }

  @Override
  protected void processStartSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      ParallelGatewayInstance flownodeInstance,
      String inputFlowId,
      Variables variables) {
    flownodeInstance.addTriggeredInputFlow(inputFlowId);
  }

  @Override
  protected void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      ParallelGatewayInstance instance) {}
}

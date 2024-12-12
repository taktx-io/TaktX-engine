package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.ExclusiveGateway;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.ExclusiveGatewayInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;

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

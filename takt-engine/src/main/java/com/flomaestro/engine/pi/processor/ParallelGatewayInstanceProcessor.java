/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.ParallelGateway;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ParallelGatewayInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

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
      Clock clock) {
    super(ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Override
  protected boolean canTriggerOutputFlows(
      ParallelGatewayInstance gatewayInstance, FlowNodeInstances flowNodeInstances) {
    return gatewayInstance.getFlowNode().getIncoming().equals(gatewayInstance.getTriggeredFlows());
  }

  @Override
  protected void processStartSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      ParallelGatewayInstance flownodeInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    flownodeInstance.addTriggeredFlow(inputFlowId);
  }

  @Override
  protected void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      ParallelGatewayInstance instance) {
    // Nothing to do
  }
}

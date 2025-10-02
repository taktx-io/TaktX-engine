/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.ParallelGateway;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ParallelGatewayInstance;
import io.taktx.engine.pi.model.Scope;
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
  protected boolean canTriggerOutputFlows(ParallelGatewayInstance gatewayInstance, Scope scope) {
    return gatewayInstance.getFlowNode().getIncoming().equals(gatewayInstance.getTriggeredFlows());
  }

  @Override
  protected void processStartSpecificGatewayInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      ParallelGatewayInstance flownodeInstance,
      String inputFlowId) {
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

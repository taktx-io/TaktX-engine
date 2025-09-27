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
import io.taktx.engine.pd.model.ExclusiveGateway;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ExclusiveGatewayInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
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
      Clock clock) {
    super(ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Override
  protected boolean canTriggerOutputFlows(ExclusiveGatewayInstance gatewayInstance, Scope scope) {
    return true;
  }

  @Override
  protected void processStartSpecificGatewayInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      ExclusiveGatewayInstance flownodeInstance,
      String inputFlowId,
      VariableScope variables) {
    // nothing to do
  }

  @Override
  protected void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      ExclusiveGatewayInstance instance) {
    // nothing to do
  }
}

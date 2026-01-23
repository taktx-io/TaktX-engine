/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class BoundaryEventInstanceProcessor
    extends CatchEventInstanceProcessor<BoundaryEvent, BoundaryEventInstance> {

  @Inject
  BoundaryEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Override
  protected void processStartSpecificCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      BoundaryEventInstance flowNodeInstance) {
    if (flowNodeInstance.getFlowNode().isCancelActivity()) {
      Long attachedInstanceId = flowNodeInstance.getAttachedInstanceId();
      FlowNodeInstance<?> attachedInstance =
          scope.getFlowNodeInstances().getInstanceWithInstanceId(attachedInstanceId);
      scope.getDirectInstanceResult().addAbortInstance(attachedInstance);
    }
    flowNodeInstance.setState(ExecutionState.COMPLETED);
  }

  @Override
  protected void processContinueSpecificCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      BoundaryEventInstance boundaryEventInstance) {
    throw new IllegalStateException("We should never continue a boundary event instance");
  }
}

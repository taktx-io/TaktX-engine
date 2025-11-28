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
import io.taktx.engine.pd.model.EndEvent;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.EndEventInstance;
import io.taktx.engine.pi.model.Scope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class EndEventInstanceProcessor
    extends ThrowEventInstanceProcessor<EndEvent, EndEventInstance> {

  @Inject
  public EndEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      FeelExpressionHandler feelExpressionHandler,
      Clock clock) {

    super(ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Override
  protected void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      EndEventInstance instance) {
    // nothing to do
  }

  @Override
  protected void processStartSpecificThrowEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      EndEventInstance flowNodeInstance) {
    flowNodeInstance.setState(ExecutionState.COMPLETED);
  }
}

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
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartEventInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class StartEventInstanceProcessor
    extends CatchEventInstanceProcessor<StartEvent, StartEventInstance> {

  @Inject
  public StartEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      FeelExpressionHandler feelExpressionHandler,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Override
  protected boolean shoudHandleTimerEvents() {
    return false;
  }

  @Override
  protected void processStartSpecificEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      StartEventInstance startEventInstance,
      String inputFlowId) {
    startEventInstance.setState(ExecutionState.COMPLETED);
  }

  @Override
  protected void processContinueSpecificCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      StartEventInstance flowNodeInstance) {
    // No specific processing for continue
  }

  @Override
  protected boolean shouldCancel(StartEventInstance flowNodeInstance) {
    return true;
  }
}

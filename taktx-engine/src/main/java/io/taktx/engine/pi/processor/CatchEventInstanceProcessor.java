/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.CatchEvent;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CatchEventInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class CatchEventInstanceProcessor<
        E extends CatchEvent, I extends CatchEventInstance<? extends CatchEvent>>
    extends EventInstanceProcessor<E, I> {

  @Getter private FeelExpressionHandler feelExpressionHandler;

  protected CatchEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      FeelExpressionHandler feelExpressionHandler,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected void processStartSpecificEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I catchEventInstance,
      String inputFlowId) {

    processStartSpecificCatchEventInstance(
        processInstanceProcessingContext, scope, variableScope, catchEventInstance);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger) {
    flowNodeInstance.setState(ExecutionState.COMPLETED);

    processContinueSpecificCatchEventInstance(
        processInstanceProcessingContext, scope, flowNodeInstance);
  }

  protected abstract void processStartSpecificCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance);

  protected abstract void processContinueSpecificCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flowNodeInstance);

  @Override
  protected void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I instance) {
    // No specific abort logic for catch events
  }
}

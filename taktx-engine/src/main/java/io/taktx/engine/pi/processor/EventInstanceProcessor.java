/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.engine.pd.model.Event;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.EventInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.Set;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class EventInstanceProcessor<E extends Event, I extends EventInstance<?>>
    extends FlowNodeInstanceProcessor<E, I, ContinueFlowElementTriggerDTO> {

  protected EventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processStartSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance,
      String inputFlowId) {
    processStartSpecificEventInstance(
        processInstanceProcessingContext, scope, variableScope, flowNodeInstance, inputFlowId);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger) {
    throw new IllegalStateException("We should never continue an event instance");
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      Scope scope,
      VariableScope variableScope) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  protected abstract void processStartSpecificEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance,
      String inputFlowId);
}

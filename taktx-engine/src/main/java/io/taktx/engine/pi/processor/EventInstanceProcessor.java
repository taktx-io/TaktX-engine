/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.engine.pd.model.Event;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.EventInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.ProcessInstance;
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
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flowNodeInstance,
      String inputFlowId,
      VariableScope variables) {
    processStartSpecificEventInstance(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        flowNodeInstance,
        inputFlowId,
        variables);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope variables) {
    // Should not occur
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  protected abstract void processStartSpecificEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flowNodeInstance,
      String inputFlowId,
      VariableScope variables);
}

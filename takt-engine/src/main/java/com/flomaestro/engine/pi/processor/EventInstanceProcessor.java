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

import com.flomaestro.engine.pd.model.Event;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.SequenceFlow;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.EventInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import java.time.Clock;
import java.util.Set;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

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
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    processStartSpecificEventInstance(
        processInstance,
        instanceResult,
        directInstanceResult,
        flowElements,
        flowNodeInstance,
        inputFlowId,
        variables,
        processingStatistics);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope variables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {
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
      ProcessInstance processInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics);
}

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

import com.flomaestro.engine.pd.model.EndEvent;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.EndEventInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
@NoArgsConstructor
public class EndEventInstanceProcessor
    extends ThrowEventInstanceProcessor<EndEvent, EndEventInstance> {

  @Inject
  public EndEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      EndEventInstance instance,
      ProcessInstance processInstance,
      VariableScope currentVariableScope,
      ProcessingStatistics processingStatistics,
      FlowElements flowElements) {
    // nothing to do
  }

  @Override
  protected void processStartSpecificThrowEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      EndEventInstance flowNodeInstance,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    // nothing to do
  }
}

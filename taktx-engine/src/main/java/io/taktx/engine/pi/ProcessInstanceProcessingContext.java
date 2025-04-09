/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi;

import io.taktx.dto.v_1_0_0.FlowNodeInstanceDTO;
import io.taktx.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import io.taktx.engine.pi.model.ProcessInstance;
import lombok.Builder;
import lombok.Getter;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * Context object that encapsulates parameters commonly passed between processor methods. This
 * reduces parameter lists and improves maintainability.
 */
@Getter
public class ProcessInstanceProcessingContext {
  private final KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final InstanceResult instanceResult;
  private final ProcessInstance processInstance;
  private final ProcessingStatistics processingStatistics;

  @Builder
  public ProcessInstanceProcessingContext(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      ProcessingStatistics processingStatistics) {
    this.flowNodeInstanceStore = flowNodeInstanceStore;
    this.instanceResult = instanceResult;
    this.processInstance = processInstance;
    this.processingStatistics = processingStatistics;
  }
}

/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.generic.TopicMonitor;
import io.taktx.engine.pi.model.ProcessInstance;
import lombok.Builder;
import lombok.Getter;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

/**
 * Context object that encapsulates parameters commonly passed between processor methods. This
 * reduces parameter lists and improves maintainability.
 */
@Getter
public class ProcessInstanceProcessingContext {
  private final KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final ReadOnlyKeyValueStore<String, ValueAndTimestamp<TopicMetaDTO>>
      externalTaskMetaStore;
  private final TopicMonitor topicStore;
  private final InstanceResult instanceResult;
  private final ProcessInstance processInstance;
  private final ProcessingStatistics processingStatistics;

  @Builder
  public ProcessInstanceProcessingContext(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      ReadOnlyKeyValueStore<String, ValueAndTimestamp<TopicMetaDTO>> externalTaskMetaStore,
      TopicMonitor topicStore,
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      ProcessingStatistics processingStatistics) {
    this.flowNodeInstanceStore = flowNodeInstanceStore;
    this.externalTaskMetaStore = externalTaskMetaStore;
    this.topicStore = topicStore;
    this.instanceResult = instanceResult;
    this.processInstance = processInstance;
    this.processingStatistics = processingStatistics;
  }
}

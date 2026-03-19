/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.topicmanagement.DynamicTopicManager;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * Context object that encapsulates parameters commonly passed between processor methods. This
 * reduces parameter lists and improves maintainability.
 */
@Getter
public class ProcessInstanceProcessingContext {
  private final KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final DynamicTopicManager topicManager;
  private final InstanceResult instanceResult;
  private final ProcessInstance processInstance;
  private final ProcessingStatistics processingStatistics;

  /** Structured trust data for the command currently being processed, if any. */
  @Setter @Nullable private CommandTrustMetadataDTO currentTrustMetadata;

  /** Structured provenance/trust data for the original command chain, if any. */
  @Setter @Nullable private CommandTrustMetadataDTO originTrustMetadata;

  @Builder
  public ProcessInstanceProcessingContext(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      DynamicTopicManager topicManager,
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      ProcessingStatistics processingStatistics) {
    this.flowNodeInstanceStore = flowNodeInstanceStore;
    this.topicManager = topicManager;
    this.instanceResult = instanceResult;
    this.processInstance = processInstance;
    this.processingStatistics = processingStatistics;
  }
}

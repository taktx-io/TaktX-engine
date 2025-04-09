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

import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pi.model.FlowNodeInstances;
import lombok.Getter;

/**
 * Context object that encapsulates parameters commonly passed between processor methods. This
 * reduces parameter lists and improves maintainability.
 */
@Getter
public class FlowNodeInstanceProcessingContext {

  private final FlowNodeInstances flowNodeInstances;
  private final FlowElements flowElements;
  private final DirectInstanceResult directInstanceResult;

  public FlowNodeInstanceProcessingContext(
      FlowNodeInstances flowNodeInstances, FlowElements flowElements) {
    this(flowNodeInstances, flowElements, DirectInstanceResult.empty());
  }

  public FlowNodeInstanceProcessingContext(
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      DirectInstanceResult directInstanceResult) {
    this.flowNodeInstances = flowNodeInstances;
    this.flowElements = flowElements;
    this.directInstanceResult = directInstanceResult;
  }
}

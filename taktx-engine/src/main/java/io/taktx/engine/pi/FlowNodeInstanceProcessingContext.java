/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pi.model.Scope;
import lombok.Getter;

/**
 * Context object that encapsulates parameters commonly passed between processor methods. This
 * reduces parameter lists and improves maintainability.
 */
@Getter
public class FlowNodeInstanceProcessingContext {
  private final Scope scope;
  private final FlowElements flowElements;
  private final DirectInstanceResult directInstanceResult;
  private final int subProcessLevel;

  public FlowNodeInstanceProcessingContext(
      Scope scope, int subProcessLevel, FlowElements flowElements) {
    this(scope, flowElements, subProcessLevel, DirectInstanceResult.empty());
  }

  public FlowNodeInstanceProcessingContext(
      Scope scope,
      FlowElements flowElements,
      int subProcessLevel,
      DirectInstanceResult directInstanceResult) {
    this.scope = scope;
    this.flowElements = flowElements;
    this.subProcessLevel = subProcessLevel;
    this.directInstanceResult = directInstanceResult;
  }
}

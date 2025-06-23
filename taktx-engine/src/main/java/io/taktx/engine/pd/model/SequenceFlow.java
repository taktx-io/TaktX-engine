/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import io.taktx.dto.FlowConditionDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class SequenceFlow extends FlowElement {
  private String source;
  private String target;
  private FlowConditionDTO condition;

  @Setter private FlowNode sourceNode;
  @Setter private FlowNode targetNode;
}

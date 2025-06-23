/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FlowNodeInstanceUpdateDTO extends InstanceUpdateDTO {
  private List<Long> flowNodeInstancePath;

  private FlowNodeInstanceDTO flowNodeInstance;

  private VariablesDTO variables;

  private long processTime;

  public FlowNodeInstanceUpdateDTO(
      List<Long> flowNodeInstancePath,
      FlowNodeInstanceDTO flowNodeInstance,
      VariablesDTO variables,
      long processTime) {
    this.flowNodeInstancePath = flowNodeInstancePath;
    this.flowNodeInstance = flowNodeInstance;
    this.variables = variables;
    this.processTime = processTime;
  }
}

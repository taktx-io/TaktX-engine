/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
public class FlowNodeInstanceUpdateDTO extends InstanceUpdateDTO {

  private List<Long> flowNodeInstancePath;

  private FlowNodeInstanceDTO flowNodeInstance;

  private VariablesDTO variables;

  private long processTime;

  private String inputSequenceFlowId;

  private List<String> outputSequenceFlowIds;

  public FlowNodeInstanceUpdateDTO(
      List<Long> flowNodeInstancePath,
      FlowNodeInstanceDTO flowNodeInstance,
      VariablesDTO variables,
      long processTime,
      String inputSequenceFlowId,
      List<String> outputSequenceFlowIds) {
    this.flowNodeInstancePath = flowNodeInstancePath;
    this.flowNodeInstance = flowNodeInstance;
    this.variables = variables;
    this.processTime = processTime;
    this.inputSequenceFlowId = inputSequenceFlowId;
    this.outputSequenceFlowIds = outputSequenceFlowIds;
  }
}

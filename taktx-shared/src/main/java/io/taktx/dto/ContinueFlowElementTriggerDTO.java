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
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
public class ContinueFlowElementTriggerDTO extends ProcessInstanceTriggerDTO {

  private String inputFlowId;
  private VariablesDTO variables;
  private List<Long> elementInstanceIdPath;

  public ContinueFlowElementTriggerDTO(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String inputFlowId,
      VariablesDTO variables) {
    super(processInstanceId);
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.inputFlowId = inputFlowId;
    this.variables = variables;
  }
}

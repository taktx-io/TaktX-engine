/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class ProcessInstanceDTO {
  private UUID processInstanceId;

  private UUID parentProcessInstanceId;

  private FlowNodeInstancesDTO flowNodeInstances;

  private List<Long> parentElementInstancePath;

  private ProcessDefinitionKey processDefinitionKey;

  private boolean propagateAllToParent;

  private Set<IoVariableMappingDTO> outputMappings;

  public ProcessInstanceDTO(
      UUID processInstanceId,
      UUID parentProcessInstanceId,
      FlowNodeInstancesDTO flowNodeInstances,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings) {
    this.processInstanceId = processInstanceId;
    this.parentProcessInstanceId = parentProcessInstanceId;
    this.flowNodeInstances = flowNodeInstances;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
  }
}

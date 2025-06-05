/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class StartCommandDTO extends ProcessInstanceTriggerDTO {

  private UUID parentProcessInstanceKey;

  private String elementId;

  private List<Long> parentElementInstancePath;

  private ProcessDefinitionKey processDefinitionKey;

  private boolean propagateAllToParent;

  private Set<IoVariableMappingDTO> outputMappings;

  public StartCommandDTO(
      UUID processInstanceKey,
      String elementId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      VariablesDTO variables) {
    this(
        processInstanceKey,
        null,
        elementId,
        parentElementInstancePath,
        processDefinitionKey,
        variables,
        false,
        Set.of());
  }

  public StartCommandDTO(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      String elementId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      VariablesDTO variables,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings) {
    super(processInstanceKey, variables);
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.elementId = elementId;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
  }
}

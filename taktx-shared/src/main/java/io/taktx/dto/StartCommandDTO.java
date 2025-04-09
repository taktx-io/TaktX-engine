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

import com.fasterxml.jackson.annotation.JsonProperty;
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

  @JsonProperty("ppi")
  private UUID parentProcessInstanceKey;

  @JsonProperty("peip")
  private List<Long> parentElementInstancePath;

  @JsonProperty("pd")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("pp")
  private boolean propagateAllToParent;

  @JsonProperty("om")
  private Set<IoVariableMappingDTO> outputMappings;

  public StartCommandDTO(
      UUID processInstanceKey,
      List<String> elementIds,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      VariablesDTO variables) {
    this(
        processInstanceKey,
        null,
        elementIds,
        parentElementInstancePath,
        processDefinitionKey,
        variables,
        false,
        Set.of());
  }

  public StartCommandDTO(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      List<String> elementIds,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      VariablesDTO variables,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings) {
    super(processInstanceKey, elementIds, variables);
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
  }
}

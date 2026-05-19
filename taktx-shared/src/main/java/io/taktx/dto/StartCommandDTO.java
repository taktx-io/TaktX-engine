/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import jakarta.annotation.Nullable;
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

  private UUID parentProcessInstanceId;

  private String elementId;

  private List<Long> parentElementInstancePath;

  private ProcessDefinitionKey processDefinitionKey;

  private VariablesDTO variables;

  private boolean propagateAllToParent;

  private Set<IoVariableMappingDTO> outputMappings;

  // Business metadata — appended last for CBOR array backward compatibility
  @Nullable private String businessKey;

  private Set<String> tags;

  public StartCommandDTO(
      UUID processInstanceId,
      String elementId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      VariablesDTO variables) {
    this(
        processInstanceId,
        null,
        elementId,
        parentElementInstancePath,
        processDefinitionKey,
        variables,
        false,
        Set.of(),
        null,
        Set.of());
  }

  public StartCommandDTO(
      UUID processInstanceId,
      UUID parentProcessInstanceId,
      String elementId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      VariablesDTO variables,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings) {
    this(
        processInstanceId,
        parentProcessInstanceId,
        elementId,
        parentElementInstancePath,
        processDefinitionKey,
        variables,
        propagateAllToParent,
        outputMappings,
        null,
        Set.of());
  }

  public StartCommandDTO(
      UUID processInstanceId,
      UUID parentProcessInstanceId,
      String elementId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      VariablesDTO variables,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings,
      @Nullable String businessKey,
      Set<String> tags) {
    super(processInstanceId);
    this.parentProcessInstanceId = parentProcessInstanceId;
    this.elementId = elementId;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.variables = variables;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
    this.businessKey = businessKey;
    this.tags = tags != null ? tags : Set.of();
  }
}

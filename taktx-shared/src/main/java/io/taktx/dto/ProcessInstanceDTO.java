/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;
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

  private ScopeDTO scope;

  private List<Long> parentElementInstancePath;

  private ProcessDefinitionKey processDefinitionKey;

  private boolean propagateAllToParent;

  private Set<IoVariableMappingDTO> outputMappings;

  private IncidentInfoDTO incidentInfo;

  // Business metadata — appended last for CBOR array backward compatibility
  @Nullable private String businessKey;

  private Set<String> tags;

  public ProcessInstanceDTO(
      UUID processInstanceId,
      UUID parentProcessInstanceId,
      ScopeDTO scope,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings,
      IncidentInfoDTO incidentInfo) {
    this(
        processInstanceId,
        parentProcessInstanceId,
        scope,
        parentElementInstancePath,
        processDefinitionKey,
        propagateAllToParent,
        outputMappings,
        incidentInfo,
        null,
        Set.of());
  }

  public ProcessInstanceDTO(
      UUID processInstanceId,
      UUID parentProcessInstanceId,
      ScopeDTO scope,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      boolean propagateAllToParent,
      Set<IoVariableMappingDTO> outputMappings,
      IncidentInfoDTO incidentInfo,
      @Nullable String businessKey,
      Set<String> tags) {
    this.processInstanceId = processInstanceId;
    this.parentProcessInstanceId = parentProcessInstanceId;
    this.scope = scope;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
    this.incidentInfo = incidentInfo;
    this.businessKey = businessKey;
    this.tags = tags != null ? tags : Set.of();
  }
}

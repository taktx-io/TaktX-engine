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

/**
 * Process-instance update DTO backed by {@code instance_update.proto} / {@code
 * ProcessInstanceUpdateMessage}. Business metadata maps to {@code business_key = 11} and {@code
 * tags = 12}.
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProcessInstanceUpdateDTO extends InstanceUpdateDTO {
  private UUID parentProcessInstanceId;

  private List<Long> parentElementInstancePath;

  private ProcessDefinitionKey processDefinitionKey;

  private IncidentInfoDTO incidentInfoDTO;

  private ScopeDTO scope;

  private VariablesDTO variables;

  private Long processStartTime;
  private Long processEndTime;

  // Proto mapping: instance_update.proto / ProcessInstanceUpdateMessage.business_key = 11
  @Nullable private String businessKey;

  private Set<String> tags;

  public ProcessInstanceUpdateDTO(
      UUID parentProcessInstanceId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      IncidentInfoDTO incidentInfoDTO,
      ScopeDTO scope,
      VariablesDTO variables,
      Long processStartTime,
      Long processEndTime) {
    this(
        parentProcessInstanceId,
        parentElementInstancePath,
        processDefinitionKey,
        incidentInfoDTO,
        scope,
        variables,
        processStartTime,
        processEndTime,
        null,
        Set.of());
  }

  public ProcessInstanceUpdateDTO(
      UUID parentProcessInstanceId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      IncidentInfoDTO incidentInfoDTO,
      ScopeDTO scope,
      VariablesDTO variables,
      Long processStartTime,
      Long processEndTime,
      @Nullable String businessKey,
      Set<String> tags) {
    this.parentProcessInstanceId = parentProcessInstanceId;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.incidentInfoDTO = incidentInfoDTO;
    this.scope = scope;
    this.variables = variables;
    this.processStartTime = processStartTime;
    this.processEndTime = processEndTime;
    this.businessKey = businessKey;
    this.tags = tags != null ? tags : Set.of();
  }

  public ProcessInstanceUpdateDTO(
      ProcessInstanceDTO processInstance,
      VariablesDTO variables,
      Long processStartTime,
      Long processEndTime) {
    this(
        processInstance.getParentProcessInstanceId(),
        processInstance.getParentElementInstancePath(),
        processInstance.getProcessDefinitionKey(),
        processInstance.getIncidentInfo(),
        processInstance.getScope(),
        variables,
        processStartTime,
        processEndTime,
        null, // businessKey — only emitted on the initial start update
        Set.of()); // tags — only emitted on the initial start update
  }

  /**
   * Convenience constructor for the initial start update that includes business metadata.
   * Subsequent state-change updates should use the 4-arg constructor which omits them.
   */
  public ProcessInstanceUpdateDTO(
      ProcessInstanceDTO processInstance,
      VariablesDTO variables,
      Long processStartTime,
      Long processEndTime,
      @Nullable String businessKey,
      Set<String> tags) {
    this(
        processInstance.getParentProcessInstanceId(),
        processInstance.getParentElementInstancePath(),
        processInstance.getProcessDefinitionKey(),
        processInstance.getIncidentInfo(),
        processInstance.getScope(),
        variables,
        processStartTime,
        processEndTime,
        businessKey,
        tags);
  }
}

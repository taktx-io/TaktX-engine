/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
public class ProcessInstanceUpdateDTO extends InstanceUpdateDTO {
  private UUID parentProcessInstanceId;

  private List<Long> parentElementInstancePath;

  private ProcessDefinitionKey processDefinitionKey;

  private IncidentInfoDTO incidentInfoDTO;

  private ScopeDTO scope;

  private VariablesDTO variables;

  private Long processStartTime;
  private Long processEndTime;

  public ProcessInstanceUpdateDTO(
      UUID parentProcessInstanceId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      IncidentInfoDTO incidentInfoDTO,
      ScopeDTO scope,
      VariablesDTO variables,
      Long processStartTime,
      Long processEndTime) {

    this.parentProcessInstanceId = parentProcessInstanceId;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.incidentInfoDTO = incidentInfoDTO;
    this.scope = scope;
    this.variables = variables;
    this.processStartTime = processStartTime;
    this.processEndTime = processEndTime;
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
        processEndTime);
  }
}

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
public class ProcessInstanceUpdateDTO extends InstanceUpdateDTO {
  private UUID parentProcessInstanceId;

  private List<Long> parentElementInstancePath;

  private ProcessDefinitionKey processDefinitionKey;

  private IncidentInfoDTO incidentInfoDTO;

  private ScopeDTO scope;

  private VariablesDTO variables;

  private long processTime;

  public ProcessInstanceUpdateDTO(
      UUID parentProcessInstanceId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      IncidentInfoDTO incidentInfoDTO,
      ScopeDTO scope,
      VariablesDTO variables,
      long processTime) {

    this.parentProcessInstanceId = parentProcessInstanceId;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.incidentInfoDTO = incidentInfoDTO;
    this.scope = scope;
    this.variables = variables;
    this.processTime = processTime;
  }

  public ProcessInstanceUpdateDTO(
      ProcessInstanceDTO processInstance, VariablesDTO variables, long processTime) {
    this(
        processInstance.getParentProcessInstanceId(),
        processInstance.getParentElementInstancePath(),
        processInstance.getProcessDefinitionKey(),
        processInstance.getIncidentInfo(),
        processInstance.getScope(),
        variables,
        processTime);
  }
}

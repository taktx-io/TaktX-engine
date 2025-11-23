/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pi.IncidentInfo;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
public class ProcessInstance {
  private final UUID processInstanceId;
  private final UUID parentProcessInstanceId;
  private final List<Long> parentElementInstancePath;
  private final ProcessDefinitionKey processDefinitionKey;
  private final Scope scope;
  private final boolean propagateAllToParent;
  private final Set<IoVariableMapping> outputMappings;
  @Setter private IncidentInfo incidentInfo;

  public ProcessInstance(
      UUID processInstanceId,
      UUID parentProcessInstanceId,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      Scope scope,
      boolean propagateAllToParent,
      Set<IoVariableMapping> outputMappings) {
    this.processInstanceId = processInstanceId;
    this.parentProcessInstanceId = parentProcessInstanceId;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.scope = scope;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
  }
}

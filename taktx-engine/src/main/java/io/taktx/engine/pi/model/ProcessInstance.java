/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.engine.pd.model.IoVariableMapping;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ProcessInstance {
  private final UUID processInstanceKey;
  private final UUID parentProcessInstanceKey;
  private final List<Long> parentElementInstancePath;
  private final ProcessDefinitionKey processDefinitionKey;
  private final FlowNodeInstances flowNodeInstances;
  private final boolean propagateAllToParent;
  private final Set<IoVariableMapping> outputMappings;

  public ProcessInstance(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      List<Long> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      FlowNodeInstances flowNodeInstances,
      boolean propagateAllToParent,
      Set<IoVariableMapping> outputMappings) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeInstances = flowNodeInstances;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
  }
}

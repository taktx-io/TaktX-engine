package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.IoVariableMapping;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ProcessInstance {
  private final UUID processInstanceKey;
  private final UUID parentProcessInstanceKey;
  private final List<String> parentElementIdPath;
  private final List<UUID> parentElementInstancePath;
  private final ProcessDefinitionKey processDefinitionKey;
  private final FlowNodeInstances flowNodeInstances;
  private final boolean propagateAllToParent;
  private final Set<IoVariableMapping> outputMappings;

  public ProcessInstance(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      List<String> parentElementIdPath,
      List<UUID> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      FlowNodeInstances flowNodeInstances,
      boolean propagateAllToParent,
      Set<IoVariableMapping> outputMappings) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeInstances = flowNodeInstances;
    this.propagateAllToParent = propagateAllToParent;
    this.outputMappings = outputMappings;
  }
}

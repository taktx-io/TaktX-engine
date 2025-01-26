package com.flomaestro.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.streams.state.KeyValueStore;

public class ProcessInstanceVariables extends AbstractVariableScope {
  private final Map<UUID, FlowNodeInstancesVariables> flowNodeInstancesVariables = new HashMap<>();

  public ProcessInstanceVariables(
      KeyValueStore<VariableKeyDTO, JsonNode> variableStore, UUID processInstanceKey) {
    super(variableStore, processInstanceKey, Constants.MIN_UUID, Constants.MIN_UUID, null);
  }

  public static ProcessInstanceVariables empty() {
    return new ProcessInstanceVariables(null, null);
  }

  public FlowNodeInstancesVariables selectFlowNodeInstancesScope(UUID flowNodeInstancesKey) {
    return this.flowNodeInstancesVariables.computeIfAbsent(
        flowNodeInstancesKey,
        k -> new FlowNodeInstancesVariables(variableStore, flowNodeInstancesKey, this));
  }

  public Map<String, JsonNode> retrieveAndFlattenAll() {
    Map<String, JsonNode> retrieved = retrieveAllInScope();
    flowNodeInstancesVariables.values().forEach(scope -> retrieved.putAll(scope.retrieveAndFlattenAll()));
    return retrieved;
  }

  public Map<String, JsonNode> persist() {
    Map<String, JsonNode> stored = persistScope();
    flowNodeInstancesVariables.values().forEach(variable -> stored.putAll(variable.persist()));
    return stored;
  }
}

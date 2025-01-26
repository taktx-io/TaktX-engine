package com.flomaestro.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.streams.state.KeyValueStore;

public class FlowNodeInstanceVariables extends AbstractVariableScope {
  private final Map<UUID, FlowNodeInstancesVariables> flowNodeInstancesVariables = new HashMap<>();

  protected FlowNodeInstanceVariables(
      KeyValueStore<VariableKeyDTO, JsonNode> variableStore,
      UUID processInstanceKey,
      UUID flowNodeInstancesKey,
      UUID elementInstanceKey,
      AbstractVariableScope parentScope) {
    super(variableStore, processInstanceKey, flowNodeInstancesKey, elementInstanceKey, parentScope);
  }

  public FlowNodeInstancesVariables selectFlowNodeInstancesScope(UUID flowNodeInstanceKey) {
    return this.flowNodeInstancesVariables.computeIfAbsent(
        flowNodeInstanceKey,
        k -> new FlowNodeInstancesVariables(variableStore, flowNodeInstanceKey, this));
  }

  Map<String, JsonNode> persist() {
    Map<String, JsonNode> stored = persistScope();
    flowNodeInstancesVariables.values().forEach(variable -> stored.putAll(variable.persist()));
    return stored;
  }

  public Map<String, JsonNode> retrieveAndFlattenAll() {
    Map<String, JsonNode> retrieved = retrieveAllInScope();
    flowNodeInstancesVariables.values().forEach(scope -> retrieved.putAll(scope.retrieveAndFlattenAll()));
    return retrieved;
  }
}

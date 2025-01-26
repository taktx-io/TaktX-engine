package com.flomaestro.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.streams.state.KeyValueStore;

public class FlowNodeInstancesVariables extends AbstractVariableScope {
  private final Map<UUID, FlowNodeInstanceVariables> flowNodeInstanceVariables = new HashMap<>();

  public FlowNodeInstancesVariables(
      KeyValueStore<VariableKeyDTO, JsonNode> keyValueStore,
      UUID flowNodeInstancesKey,
      AbstractVariableScope parentVariableScope) {
    super(
        keyValueStore,
        parentVariableScope.getProcessInstanceKey(),
        flowNodeInstancesKey,
        Constants.MIN_UUID,
        parentVariableScope);
  }

  public FlowNodeInstanceVariables selectFlowNodeInstanceScope(UUID flowNodeInstanceKey) {
    return this.flowNodeInstanceVariables.computeIfAbsent(
        flowNodeInstanceKey,
        k ->
            new FlowNodeInstanceVariables(
                variableStore,
                getProcessInstanceKey(),
                getFlowNodeInstancesKey(),
                flowNodeInstanceKey,
                this));
  }

  public Map<String, JsonNode> persist() {
    Map<String, JsonNode> stored = persistScope();
    flowNodeInstanceVariables.values().forEach(variable -> stored.putAll(variable.persist()));
    return stored;
  }

  public Map<String, JsonNode> retrieveAndFlattenAll() {
    Map<String, JsonNode> retrieved = retrieveAllInScope();
    flowNodeInstanceVariables.values().forEach(scope -> retrieved.putAll(scope.retrieveAndFlattenAll()));
    return retrieved;
  }
}

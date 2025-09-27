/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.Constants;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.WithScope;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

public class StoredScopeWrapper {

  private final UUID processInstanceId;
  private final Scope scope;
  private final KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final FlowElements flowElements;
  private final ProcessInstanceMapper mapper;

  public StoredScopeWrapper(
      UUID processInstanceId,
      Scope scope,
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowElements flowElements,
      ProcessInstanceMapper mapper) {
    this.processInstanceId = processInstanceId;
    this.scope = scope;
    this.flowNodeInstanceStore = flowNodeInstanceStore;
    this.flowElements = flowElements;
    this.mapper = mapper;
  }

  public FlowNodeInstance<?> getInstanceWithInstanceId(long id) {
    FlowNodeInstance<?> instance = scope.getInstanceWithInstanceId(id);
    if (instance == null) {
      FlowNodeInstanceKeyDTO key = generatedKeyPath(scope, id);
      instance = getFlowNodeInstanceFromStore(key);
    }
    return instance;
  }

  public Map<Long, FlowNodeInstance<?>> getAlParentlInstances() {
    FlowNodeInstanceKeyDTO keyMin =
        generatedKeyPath(scope.getParentFlowNodeInstance().getParentInstance(), Constants.MIN_LONG);
    FlowNodeInstanceKeyDTO keyMax =
        generatedKeyPath(scope.getParentFlowNodeInstance().getParentInstance(), Constants.MAX_LONG);

    retrieveRangeFromStore(keyMin, keyMax);
    return scope.getInstances();
  }

  public Map<Long, FlowNodeInstance<?>> getAllInstances() {
    FlowNodeInstanceKeyDTO keyMin = generatedKeyPath(scope, Constants.MIN_LONG);
    FlowNodeInstanceKeyDTO keyMax = generatedKeyPath(scope, Constants.MAX_LONG);

    retrieveRangeFromStore(keyMin, keyMax);
    return scope.getInstances();
  }

  private void retrieveRangeFromStore(
      FlowNodeInstanceKeyDTO keyMin, FlowNodeInstanceKeyDTO keyMax) {
    try (KeyValueIterator<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> range =
        flowNodeInstanceStore.range(keyMin, keyMax)) {
      range.forEachRemaining(
          entry -> {
            FlowNodeInstanceDTO value = entry.value;
            FlowNodeInstance<?> instance = mapper.map(value, flowElements);
            instance.setParentInstance(scope.getParentFlowNodeInstance());
            scope
                .getInstances()
                .putIfAbsent(entry.key.getFlowNodeInstanceKeyPath().getLast(), instance);
          });
    }
  }

  private FlowNodeInstance<?> getFlowNodeInstanceFromStore(FlowNodeInstanceKeyDTO keyPath) {
    FlowNodeInstanceDTO storedFlowNodeInstanceDTO = flowNodeInstanceStore.get(keyPath);
    FlowNodeInstance<?> flowNodeInstance = null;
    if (storedFlowNodeInstanceDTO != null) {
      flowNodeInstance = mapper.map(storedFlowNodeInstanceDTO, flowElements);
      if (flowNodeInstance != null) {
        flowNodeInstance.setParentInstance(scope.getParentFlowNodeInstance());
        if (flowNodeInstance instanceof WithScope withScope) {
          withScope.getScope().setParentFlowNodeInstance(flowNodeInstance);
        }
        scope.putInstance(flowNodeInstance);
      }
    }
    return flowNodeInstance;
  }

  private FlowNodeInstanceKeyDTO generatedKeyPath(Scope parentScope, long id) {
    LinkedList<Long> keyPath = new LinkedList<>();
    keyPath.addFirst(id);
    FlowNodeInstance<?> parentFlowNodeInstance = parentScope.getParentFlowNodeInstance();
    while (parentFlowNodeInstance != null) {
      keyPath.addFirst(parentFlowNodeInstance.getElementInstanceId());
      parentFlowNodeInstance = parentFlowNodeInstance.getParentInstance();
    }
    return new FlowNodeInstanceKeyDTO(processInstanceId, keyPath);
  }

  private FlowNodeInstanceKeyDTO generatedKeyPath(FlowNodeInstance<?> flowNodeInstance, Long id) {
    LinkedList<Long> keyPath = new LinkedList<>();
    keyPath.addFirst(id);
    while (flowNodeInstance != null) {
      keyPath.addFirst(flowNodeInstance.getElementInstanceId());
      flowNodeInstance = flowNodeInstance.getParentInstance();
    }
    return new FlowNodeInstanceKeyDTO(processInstanceId, keyPath);
  }
}

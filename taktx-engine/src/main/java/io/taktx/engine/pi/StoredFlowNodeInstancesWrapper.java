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
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.WithFlowNodeInstances;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.mapstruct.factory.Mappers;

public class StoredFlowNodeInstancesWrapper {

  private final UUID processInstanceId;
  private final FlowNodeInstances flowNodeInstances;
  private final KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final FlowElements flowElements;
  private final ProcessInstanceMapper mapper;

  public StoredFlowNodeInstancesWrapper(
      UUID processInstanceId,
      FlowNodeInstances flowNodeInstances,
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowElements flowElements,
      ProcessInstanceMapper mapper) {
    this.processInstanceId = processInstanceId;
    this.flowNodeInstances = flowNodeInstances;
    this.flowNodeInstanceStore = flowNodeInstanceStore;
    this.flowElements = flowElements;
    this.mapper = mapper;
  }

  public FlowNodeInstance<?> getInstanceWithInstanceId(long id) {
    FlowNodeInstance<?> instance = flowNodeInstances.getInstanceWithInstanceId(id);
    if (instance == null) {
      FlowNodeInstanceKeyDTO key = generatedKeyPath(flowNodeInstances, id);
      instance = getFlowNodeInstanceFromStore(key);
    }
    return instance;
  }

  public Map<Long, FlowNodeInstance<?>> getAlParentlInstances() {
    FlowNodeInstanceKeyDTO keyMin =
        generatedKeyPath(
            flowNodeInstances.getParentFlowNodeInstance().getParentInstance(), Constants.MIN_LONG);
    FlowNodeInstanceKeyDTO keyMax =
        generatedKeyPath(
            flowNodeInstances.getParentFlowNodeInstance().getParentInstance(), Constants.MAX_LONG);

    retrieveRangeFromStore(keyMin, keyMax);
    return flowNodeInstances.getInstances();
  }

  public Map<Long, FlowNodeInstance<?>> getAllInstances() {
    FlowNodeInstanceKeyDTO keyMin = generatedKeyPath(flowNodeInstances, Constants.MIN_LONG);
    FlowNodeInstanceKeyDTO keyMax = generatedKeyPath(flowNodeInstances, Constants.MAX_LONG);

    retrieveRangeFromStore(keyMin, keyMax);
    return flowNodeInstances.getInstances();
  }

  private void retrieveRangeFromStore(
      FlowNodeInstanceKeyDTO keyMin, FlowNodeInstanceKeyDTO keyMax) {
    try (KeyValueIterator<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> range =
        flowNodeInstanceStore.range(keyMin, keyMax)) {
      range.forEachRemaining(
          entry -> {
            FlowNodeInstanceDTO value = entry.value;
            FlowNodeInstance<?> instance = mapper.map(value, flowElements);
            instance.setParentInstance(flowNodeInstances.getParentFlowNodeInstance());
            flowNodeInstances
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
        flowNodeInstance.setParentInstance(flowNodeInstances.getParentFlowNodeInstance());
        if (flowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances) {
          withFlowNodeInstances.getFlowNodeInstances().setParentFlowNodeInstance(flowNodeInstance);
        }
        flowNodeInstances.putInstance(flowNodeInstance);
      }
    }
    return flowNodeInstance;
  }

  private FlowNodeInstanceKeyDTO generatedKeyPath(
      FlowNodeInstances parentFlowNodeInstances, long id) {
    LinkedList<Long> keyPath = new LinkedList<>();
    keyPath.addFirst(id);
    FlowNodeInstance<?> parentFlowNodeInstance =
        parentFlowNodeInstances.getParentFlowNodeInstance();
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

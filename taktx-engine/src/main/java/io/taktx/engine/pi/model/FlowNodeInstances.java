package io.taktx.engine.pi.model;

import io.taktx.dto.Constants;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.engine.pd.model.FlowNode;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@RequiredArgsConstructor
@Getter
@SuppressWarnings("java:S1452")
public class FlowNodeInstances {

  private final Scope scope;
  private final KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final Map<Long, FlowNodeInstance<?>> instances = new LinkedHashMap<>();

  public FlowNodeInstance<?> getInstanceWithInstanceId(long id) {
    FlowNodeInstance<?> instance = instances.get(id);
    if (instance == null) {
      FlowNodeInstanceKeyDTO key = generatedKeyPath(id);
      instance = getFlowNodeInstanceFromStore(key);
    }
    return instance;
  }

  public Optional<FlowNodeInstance<?>> getInstanceWithFlowNode(FlowNode flowNode) {
    return instances.values().stream()
        .filter(flowNodeInstance -> flowNodeInstance.getFlowNode().equals(flowNode))
        .findFirst();
  }

  public Map<Long, FlowNodeInstance<?>> getAllInstances() {
    FlowNodeInstanceKeyDTO keyMin = generatedKeyPath(Constants.MIN_LONG);
    FlowNodeInstanceKeyDTO keyMax = generatedKeyPath(Constants.MAX_LONG);

    retrieveRangeFromStore(keyMin, keyMax);
    return instances;
  }

  private void retrieveRangeFromStore(
      FlowNodeInstanceKeyDTO keyMin, FlowNodeInstanceKeyDTO keyMax) {
    try (KeyValueIterator<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> range =
        flowNodeInstanceStore.range(keyMin, keyMax)) {
      range.forEachRemaining(
          entry -> {
            FlowNodeInstanceDTO value = entry.value;
            FlowNodeInstance<?> instance = scope.mapToFlowNodeInstance(value);
            instances.putIfAbsent(entry.key.getFlowNodeInstanceKeyPath().getLast(), instance);
          });
    }
  }

  private FlowNodeInstance<?> getFlowNodeInstanceFromStore(FlowNodeInstanceKeyDTO keyPath) {
    FlowNodeInstanceDTO storedFlowNodeInstanceDTO = flowNodeInstanceStore.get(keyPath);
    FlowNodeInstance<?> flowNodeInstance = null;
    if (storedFlowNodeInstanceDTO != null) {
      flowNodeInstance = scope.mapToFlowNodeInstance(storedFlowNodeInstanceDTO);
      putInstance(flowNodeInstance);
    }

    return flowNodeInstance;
  }

  public void putInstance(FlowNodeInstance<?> fLowNodeInstance) {
    instances.put(fLowNodeInstance.getElementInstanceId(), fLowNodeInstance);
  }

  private FlowNodeInstanceKeyDTO generatedKeyPath(long id) {
    LinkedList<Long> keyPath = new LinkedList<>();
    keyPath.addFirst(id);
    if (scope.getParentFlowNodeInstance() != null) {
      keyPath.addFirst(scope.getParentFlowNodeInstance().getElementInstanceId());
    }
    Scope parentScope = scope.getParentScope();
    while (parentScope != null && parentScope.getParentFlowNodeInstance() != null) {
      keyPath.addFirst(parentScope.getParentFlowNodeInstance().getElementInstanceId());
      parentScope = parentScope.getParentScope();
    }
    return new FlowNodeInstanceKeyDTO(scope.getProcessInstanceId(), keyPath);
  }
}

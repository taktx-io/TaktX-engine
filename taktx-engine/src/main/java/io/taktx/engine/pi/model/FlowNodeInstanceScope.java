package io.taktx.engine.pi.model;

import io.taktx.dto.Constants;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.ProcessInstanceMapper;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@RequiredArgsConstructor
public class FlowNodeInstanceScope {

  private final KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final UUID processInstanceId;
  private final WithScope parentFlowNodeInstance;
  private final FlowElements flowElements;
  private final ProcessInstanceMapper processInstanceMapper;

  @Getter private Map<Long, FlowNodeInstance<?>> instances = new LinkedHashMap<>();

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
            FlowNodeInstance<?> instance = processInstanceMapper.map(value, flowElements);
            instance.setParentInstance(parentFlowNodeInstance);
            instances.putIfAbsent(entry.key.getFlowNodeInstanceKeyPath().getLast(), instance);
          });
    }
  }

  private FlowNodeInstance<?> getFlowNodeInstanceFromStore(FlowNodeInstanceKeyDTO keyPath) {
    FlowNodeInstanceDTO storedFlowNodeInstanceDTO = flowNodeInstanceStore.get(keyPath);
    FlowNodeInstance<?> flowNodeInstance = null;
    if (storedFlowNodeInstanceDTO != null) {
      flowNodeInstance = processInstanceMapper.map(storedFlowNodeInstanceDTO, flowElements);
      if (flowNodeInstance != null) {
        flowNodeInstance.setParentInstance(parentFlowNodeInstance);
        if (flowNodeInstance instanceof WithScope withScope) {
          withScope.getScope().setParentFlowNodeInstance(withScope);
        }
        putInstance(flowNodeInstance);
      }
    }
    return flowNodeInstance;
  }

  public void putInstance(FlowNodeInstance<?> fLowNodeInstance) {
    instances.put(fLowNodeInstance.getElementInstanceId(), fLowNodeInstance);
  }

  private FlowNodeInstanceKeyDTO generatedKeyPath(long id) {
    LinkedList<Long> keyPath = new LinkedList<>();
    keyPath.addFirst(id);
    WithScope parentInstance = this.parentFlowNodeInstance;
    while (parentInstance != null) {
      keyPath.addFirst(parentInstance.getElementInstanceId());
      parentInstance = parentInstance.getParentInstance();
    }
    return new FlowNodeInstanceKeyDTO(processInstanceId, keyPath);
  }

  public FlowNodeInstanceScope selectChildScope(WithScope withScope) {
    return new FlowNodeInstanceScope(
        flowNodeInstanceStore,
        processInstanceId,
        withScope,
        withScope.getFlowElements(),
        processInstanceMapper);
  }
}

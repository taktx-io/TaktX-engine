package io.taktx.engine.pi.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.Constants;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.VariableKeyDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.WIthChildElements;
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
@Getter
public class FlowNodeInstances {

  private final UUID processInstanceId;
  private final WithScope parentFlowNodeInstance;
  private final FlowElements flowElements;
  private final ProcessInstanceMapper processInstanceMapper;
  private final KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final KeyValueStore<VariableKeyDTO, JsonNode> variableStore;

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
            FlowNodeInstance<?> instance = mapToFlowNodeInstance(value);
            instances.putIfAbsent(entry.key.getFlowNodeInstanceKeyPath().getLast(), instance);
          });
    }
  }

  private FlowNodeInstance<?> mapToFlowNodeInstance(FlowNodeInstanceDTO value) {
    FlowNodeInstance<?> instance = processInstanceMapper.map(value, flowElements);
    instance.setParentInstance(parentFlowNodeInstance);
    if (instance instanceof WithScope withScope
        && instance.getFlowNode() instanceof WIthChildElements wIthChildElements) {
      Scope scope = withScope.getScope();
      scope.setParentScope(
          parentFlowNodeInstance != null ? parentFlowNodeInstance.getScope() : null);
      scope.setProcessInstanceId(processInstanceId);
      VariableScope parentVariableScope =
          parentFlowNodeInstance != null
              ? parentFlowNodeInstance.getScope().getVariableScope()
              : null;
      VariableScope variableScope =
          new VariableScope(
              parentVariableScope,
              processInstanceId,
              instance.getElementInstanceId(),
              variableStore);
      scope.setVariableScope(variableScope);
      scope.setFlowNodeInstances(
          new FlowNodeInstances(
              processInstanceId,
              withScope,
              wIthChildElements.getElements(),
              processInstanceMapper,
              flowNodeInstanceStore,
              variableStore));
      scope.setParentFlowNodeInstance(withScope);
    }
    return instance;
  }

  private FlowNodeInstance<?> getFlowNodeInstanceFromStore(FlowNodeInstanceKeyDTO keyPath) {
    FlowNodeInstanceDTO storedFlowNodeInstanceDTO = flowNodeInstanceStore.get(keyPath);
    FlowNodeInstance<?> flowNodeInstance = null;
    if (storedFlowNodeInstanceDTO != null) {
      flowNodeInstance = mapToFlowNodeInstance(storedFlowNodeInstanceDTO);
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
    WithScope parentInstance = this.parentFlowNodeInstance;
    while (parentInstance != null) {
      keyPath.addFirst(parentInstance.getElementInstanceId());
      parentInstance = parentInstance.getParentInstance();
    }
    return new FlowNodeInstanceKeyDTO(processInstanceId, keyPath);
  }
}

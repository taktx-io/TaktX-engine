package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.WithFlowNodeInstances;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.mapstruct.factory.Mappers;

public class StoredFlowNodeInstancesWrapper {

  private final UUID processInstanceKey;
  private final FlowNodeInstances flowNodeInstances;
  private final KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final FlowElements flowElements;
  private final ProcessInstanceMapper mapper;

  public StoredFlowNodeInstancesWrapper(
      UUID processInstanceKey,
      FlowNodeInstances flowNodeInstances,
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowElements flowElements) {
    this.processInstanceKey = processInstanceKey;
    this.flowNodeInstances = flowNodeInstances;
    this.flowNodeInstanceStore = flowNodeInstanceStore;
    this.flowElements = flowElements;
    this.mapper = Mappers.getMapper(ProcessInstanceMapper.class);
  }

  public FlowNodeInstance<?> getInstanceWithInstanceId(long id) {
    FlowNodeInstance<?> instance = flowNodeInstances.getInstanceWithInstanceId(id);
    if (instance == null) {
      FlowNodeInstanceKeyDTO key  = generatedKeyPath(flowNodeInstances, id);
      instance = getFlowNodeInstanceFromStore(key);
    }
    return instance;
  }

  public Map<Long, FlowNodeInstance<?>> getAllInstances() {
    FlowNodeInstanceKeyDTO keyMin = generatedKeyPath(flowNodeInstances, Constants.MIN_LONG);
    FlowNodeInstanceKeyDTO keyMax = generatedKeyPath(flowNodeInstances, Constants.MAX_LONG);

    try (KeyValueIterator<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> range =
        flowNodeInstanceStore.range(keyMin, keyMax)) {
      range.forEachRemaining(
          entry -> {
            FlowNodeInstanceDTO value = entry.value;
            FlowNodeInstance<?> instance = mapper.map(value, flowElements);
            instance.setParentInstance(flowNodeInstances.getParentFlowNodeInstance());

            flowNodeInstances.getInstances().putIfAbsent(entry.key.getFlowNodeInstanceKeyPath().getLast(), instance);
          });
    }
    return flowNodeInstances.getInstances();
  }

  private FlowNodeInstance<?> getFlowNodeInstanceFromStore(FlowNodeInstanceKeyDTO keyPath) {
    FlowNodeInstanceDTO storedFlowNodeInstanceDTO = flowNodeInstanceStore.get(keyPath);
    FlowNodeInstance<?> flowNodeInstance = null;
    if (storedFlowNodeInstanceDTO != null) {
      flowNodeInstance = mapper.map(storedFlowNodeInstanceDTO, flowElements);
      if (flowNodeInstance != null) {
        flowNodeInstance.setParentInstance(flowNodeInstances.getParentFlowNodeInstance());
        if (flowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances) {
          withFlowNodeInstances
              .getFlowNodeInstances()
              .setParentFlowNodeInstance(flowNodeInstance);
        }
        flowNodeInstances.putInstance(flowNodeInstance);
      }
    }
    return flowNodeInstance;
  }

  private FlowNodeInstanceKeyDTO generatedKeyPath(FlowNodeInstances parentFlowNodeInstances, long id) {
    LinkedList<Long> keyPath = new LinkedList<>();
    keyPath.addFirst(id);
    FlowNodeInstance<?> parentFlowNodeInstance = parentFlowNodeInstances.getParentFlowNodeInstance();
    while(parentFlowNodeInstance != null) {
      keyPath.addFirst(parentFlowNodeInstance.getElementInstanceId());
      parentFlowNodeInstance = parentFlowNodeInstance.getParentInstance();
    }
    return new FlowNodeInstanceKeyDTO(processInstanceKey, keyPath);
  }
}

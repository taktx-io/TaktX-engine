package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.WithFlowNodeInstances;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.mapstruct.factory.Mappers;

public class StoredFlowNodeInstancesWrapper {

  private final FlowNodeInstances flowNodeInstances;
  private final KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore;
  private final FlowElements flowElements;
  private final ProcessInstanceMapper mapper;

  public StoredFlowNodeInstancesWrapper(
      FlowNodeInstances flowNodeInstances,
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowElements flowElements) {
    this.flowNodeInstances = flowNodeInstances;
    this.flowNodeInstanceStore = flowNodeInstanceStore;
    this.flowElements = flowElements;
    this.mapper = Mappers.getMapper(ProcessInstanceMapper.class);
  }

  public FlowNodeInstance<?> getInstanceWithInstanceId(UUID id) {
    FlowNodeInstance<?> instance = flowNodeInstances.getInstanceWithInstanceId(id);
    if (instance == null) {
      UUID[] keyPath = generatedKeyPath(flowNodeInstances.getFlowNodeInstancesId(), id);
      instance = getFlowNodeInstanceFromStore(keyPath);
    }
    return instance;
  }

  public Map<UUID, FlowNodeInstance<?>> getAllInstances() {
    UUID[] minIdPath =
        generatedKeyPath(flowNodeInstances.getFlowNodeInstancesId(), Constants.MIN_UUID);
    UUID[] maxIdPath =
        generatedKeyPath(flowNodeInstances.getFlowNodeInstancesId(), Constants.MAX_UUID);

    try (KeyValueIterator<UUID[], FlowNodeInstanceDTO> range =
        flowNodeInstanceStore.range(minIdPath, maxIdPath)) {
      range.forEachRemaining(
          entry -> {
            FlowNodeInstanceDTO value = entry.value;
            FlowNodeInstance<?> instance = mapper.map(value, flowElements);
            flowNodeInstances.getInstances().putIfAbsent(entry.key[entry.key.length - 1], instance);
          });
    }
    return flowNodeInstances.getInstances();
  }

  private FlowNodeInstance<?> getFlowNodeInstanceFromStore(UUID[] keyPath) {
    FlowNodeInstanceDTO storedFlowNodeInstanceDTO = flowNodeInstanceStore.get(keyPath);
    FlowNodeInstance<?> flowNodeInstance = null;
    if (storedFlowNodeInstanceDTO != null) {
      flowNodeInstance = mapper.map(storedFlowNodeInstanceDTO, flowElements);
      if (flowNodeInstance != null) {
        if (flowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances) {
          withFlowNodeInstances
              .getFlowNodeInstances()
              .setParentFlowNodeInstances(flowNodeInstances);
        }
        if (flowNodeInstance.getParentElementInstanceId() != null) {
          FlowNodeInstance<?> instanceWithInstanceId =
              flowNodeInstances
                  .getParentFlowNodeInstances()
                  .getInstanceWithInstanceId(flowNodeInstance.getParentElementInstanceId());
          flowNodeInstance.setParentInstance(instanceWithInstanceId);
        }
        flowNodeInstances.putInstance(flowNodeInstance);
      }
    }
    return flowNodeInstance;
  }

  private UUID[] generatedKeyPath(UUID parentId, UUID id) {
    return new UUID[] {parentId, id};
  }
}

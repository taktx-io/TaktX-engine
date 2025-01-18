package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlowNodeInstances {

  private final Map<UUID, FlowNodeInstance<?>> instances;
  private int activeCnt;
  private ProcessInstanceState state;
  private UUID flowNodeInstancesId;
  private FlowNodeInstances parentFlowNodeInstances;
  private boolean stateChanged;

  public FlowNodeInstances() {
    this.instances = new LinkedHashMap<>();
    this.flowNodeInstancesId = UUID.randomUUID();
    this.state = ProcessInstanceState.ACTIVE;
    this.stateChanged = false;
    this.activeCnt = 0;
  }

  public void putInstance(FlowNodeInstance<?> fLowNodeInstance) {
    instances.put(fLowNodeInstance.getElementInstanceId(), fLowNodeInstance);
  }

  public FlowNodeInstance<?> getInstanceWithInstanceId(UUID elementInstanceId) {
    return instances.get(elementInstanceId);
  }

  public boolean allCompleted() {
    return instances.values().stream().allMatch(FlowNodeInstance::isCompleted);
  }

  public void determineImplicitCompletedState() {
    updateActiveCountForInstances();

    if (state == ProcessInstanceState.ACTIVE
        && instances.values().stream().allMatch(FlowNodeInstance::isNotAwaiting)) {
      if (activeCnt > 0) {
        throw new IllegalStateException("Active count is greater than 0");
      }
      this.setState(ProcessInstanceState.COMPLETED);
    } else if (state == ProcessInstanceState.ACTIVE
        && activeCnt == 0) {
      throw new IllegalStateException("Active count is 0 while some state is active");
    }
  }

  public Optional<FlowNodeInstance<?>> getInstanceWithFlowNode(FlowNode flowNode) {
    return instances.values().stream()
        .filter(flowNodeInstance -> flowNodeInstance.getFlowNode().equals(flowNode))
        .findFirst();
  }

  public void setState(ProcessInstanceState state) {
    this.stateChanged = this.state != null && this.state != state;
    this.state = state;
  }

  public boolean isStateChanged() {
    return stateChanged
        || instances.values().stream()
            .filter(WithFlowNodeInstances.class::isInstance)
            .map(WithFlowNodeInstances.class::cast)
            .anyMatch(instance -> instance.getFlowNodeInstances().isStateChanged());
  }

  public void updateActiveCountForInstances() {
    instances
        .values()
        .forEach(
            instance -> {
                if (instance.stateChanged() && instance.isAwaiting()) {
                  activeCnt++;
                } else if (instance.stateChanged() && instance.wasAwaiting()) {
                  activeCnt--;
                }
            });
  }

  public void decreaseActiveCnt() {
    activeCnt--;
  }
}

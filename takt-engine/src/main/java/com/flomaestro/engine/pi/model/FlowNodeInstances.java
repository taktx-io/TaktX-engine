package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlowNodeInstances {

  private final Map<Long, FlowNodeInstance<?>> instances;
  private int activeCnt;
  private ProcessInstanceState state;
  private boolean stateChanged;
  private FlowNodeInstance<?> parentFlowNodeInstance;
  private long elementInstanceCnt;

  public FlowNodeInstances() {
    this.instances = new LinkedHashMap<>();
    this.state = ProcessInstanceState.ACTIVE;
    this.stateChanged = false;
    this.activeCnt = 0;
    this.elementInstanceCnt = 0;
  }

  public void putInstance(FlowNodeInstance<?> fLowNodeInstance) {
    instances.put(fLowNodeInstance.getElementInstanceId(), fLowNodeInstance);
  }

  public FlowNodeInstance<?> getInstanceWithInstanceId(long elementInstanceId) {
    return instances.get(elementInstanceId);
  }

  public void determineImplicitCompletedState() {
    updateActiveCountForInstances();

    if (state == ProcessInstanceState.ACTIVE && activeCnt == 0) {
      this.setState(ProcessInstanceState.COMPLETED);
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
    for (FlowNodeInstance<?> instance : instances.values()) {
      if (instance.wasNew()) {
        activeCnt++;
      }
      if ((instance.wasNew() || instance.wasAwaiting()) && instance.isCompleted()) {
        activeCnt--;
      }
    }
    if (activeCnt < 0) {
      throw new IllegalStateException("Active count cannot be negative");
    }
  }

  public boolean isDirty() {
    return stateChanged || instances.values().stream().anyMatch(FlowNodeInstance::isDirty);
  }

  public long nextElementInstanceId() {
    return ++elementInstanceCnt;
  }
}

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
  private ProcessInstanceState state;

  private UUID flowNodeInstancesId;
  private FlowNodeInstances parentFlowNodeInstances;
  private boolean dirty;

  public FlowNodeInstances() {
    this.instances = new LinkedHashMap<>();
    this.flowNodeInstancesId = UUID.randomUUID();
    this.state = ProcessInstanceState.ACTIVE;
    this.dirty = false;
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
    if (state == ProcessInstanceState.ACTIVE
        && instances.values().stream().allMatch(FlowNodeInstance::isNotAwaiting)) {
      this.setStateDirty(ProcessInstanceState.COMPLETED);
    }
  }

  public Optional<FlowNodeInstance<?>> getInstanceWithFlowNode(FlowNode flowNode) {
    return instances.values().stream()
        .filter(flowNodeInstance -> flowNodeInstance.getFlowNode().equals(flowNode))
        .findFirst();
  }

  public void setState(ProcessInstanceState state) {
    this.state = state;
  }

  public void setStateDirty(ProcessInstanceState state) {
    this.dirty = true;
    this.state = state;
  }

  public boolean isDirty() {
    return dirty
        || instances.values().stream()
            .filter(instance -> instance instanceof WithFlowNodeInstances)
            .map(instance -> (WithFlowNodeInstances) instance)
            .anyMatch(instance -> instance.getFlowNodeInstances().isDirty());
  }
}

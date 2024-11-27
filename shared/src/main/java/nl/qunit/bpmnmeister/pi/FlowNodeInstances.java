package nl.qunit.bpmnmeister.pi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.WithFlowNodeInstances;

@Getter
@Setter
public class FlowNodeInstances {

  private final Map<UUID, FLowNodeInstance<?>> instances;
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

  public void putInstance(FLowNodeInstance<?> fLowNodeInstance) {
    instances.put(fLowNodeInstance.getElementInstanceId(), fLowNodeInstance);
  }

  public FLowNodeInstance<?> getInstanceWithInstanceId(UUID elementInstanceId) {
    return instances.get(elementInstanceId);
  }

  public boolean allCompleted() {
    return instances.values().stream().allMatch(FLowNodeInstance::isCompleted);
  }

  public void determineImplicitCompletedState() {
    if (state == ProcessInstanceState.ACTIVE
        && instances.values().stream().allMatch(FLowNodeInstance::isNotAwaiting)) {
      this.setStateDirty(ProcessInstanceState.COMPLETED);
    }
  }

  public Optional<FLowNodeInstance<?>> getInstanceWithFlowNode(FlowNode flowNode) {
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

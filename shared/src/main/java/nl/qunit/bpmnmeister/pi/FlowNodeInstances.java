package nl.qunit.bpmnmeister.pi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@Setter
public class FlowNodeInstances {

  private final Map<UUID, FLowNodeInstance<?>> instances;

  private ProcessInstanceState state;

  private FlowNodeInstances parentFlowNodeInstances;

  public FlowNodeInstances() {
    this.instances = new LinkedHashMap<>();
    this.state = ProcessInstanceState.ACTIVE;
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
      this.state = ProcessInstanceState.COMPLETED;
    }
  }

  public Optional<FLowNodeInstance<?>> getInstanceWithFlowNode(FlowNode flowNode) {
    return instances.values().stream()
        .filter(flowNodeInstance -> flowNodeInstance.getFlowNode().equals(flowNode))
        .findFirst();
  }
}

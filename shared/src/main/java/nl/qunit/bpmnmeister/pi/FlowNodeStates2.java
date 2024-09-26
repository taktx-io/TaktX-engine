package nl.qunit.bpmnmeister.pi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@Setter
public class FlowNodeStates2 {

  private final Map<UUID, FLowNodeInstance> flowNodeInstances;

  private ProcessInstanceState state;

  public FlowNodeStates2() {
    this.flowNodeInstances = new LinkedHashMap<>();
    this.state = ProcessInstanceState.ACTIVE;
  }

  public void putInstance(FLowNodeInstance fLowNodeInstance) {
    flowNodeInstances.put(fLowNodeInstance.getElementInstanceId(), fLowNodeInstance);
  }

  public FLowNodeInstance getInstanceWithInstanceId(UUID elementInstanceId) {
    return flowNodeInstances.get(elementInstanceId);
  }

  public boolean allCompleted() {
    return flowNodeInstances.values().stream().allMatch(FLowNodeInstance::isCompleted);
  }

  public void determineImplicitCompletedState() {
    if (state == ProcessInstanceState.ACTIVE
        && flowNodeInstances.values().stream().allMatch(FLowNodeInstance::isNotAwaiting)) {
      this.state = ProcessInstanceState.COMPLETED;
    }
  }

  public Optional<FLowNodeInstance> getInstanceWithFlowNode(FlowNode2 flowNode) {
    return flowNodeInstances.values().stream()
        .filter(flowNodeInstance -> flowNodeInstance.getFlowNode().equals(flowNode))
        .findFirst();
  }
}

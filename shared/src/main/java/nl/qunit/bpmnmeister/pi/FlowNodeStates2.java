package nl.qunit.bpmnmeister.pi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;
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

  public FLowNodeInstance get(UUID elementInstanceId) {
    return flowNodeInstances.get(elementInstanceId);
  }

  public boolean allMatch(Predicate<FLowNodeInstance> predicate) {
    return flowNodeInstances.values().stream().allMatch(predicate);
  }
}

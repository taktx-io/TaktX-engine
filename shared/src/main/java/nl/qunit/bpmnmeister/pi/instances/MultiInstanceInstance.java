package nl.qunit.bpmnmeister.pi.instances;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Activity2;

@NoArgsConstructor
@Getter
public class MultiInstanceInstance extends ActivityInstance {
  private List<FLowNodeInstance> instances;

  public MultiInstanceInstance(Activity2 activity2, FLowNodeInstance parentInstance) {
    super(activity2.getId(), parentInstance);
    this.instances = new ArrayList<>();
  }

  public void addInstance(FLowNodeInstance instance) {
    instances.add(instance);
  }

  public boolean allCompleted() {
    return instances.stream().allMatch(FLowNodeInstance::isNotAwaiting);
  }
}

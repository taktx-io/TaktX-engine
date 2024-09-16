package nl.qunit.bpmnmeister.pi;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Task2;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@NoArgsConstructor
public class TaskInstance extends ActivityInstance<Task2> {
  public TaskInstance(FLowNodeInstance parentInstance, Task2 flowNode) {
    super(parentInstance, flowNode);
  }
}

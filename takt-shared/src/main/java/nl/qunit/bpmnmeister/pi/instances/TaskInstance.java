package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Task;

@NoArgsConstructor
public class TaskInstance extends ActivityInstance<Task> {
  public TaskInstance(FLowNodeInstance<?> parentInstance, Task flowNode) {
    super(parentInstance, flowNode);
  }
}

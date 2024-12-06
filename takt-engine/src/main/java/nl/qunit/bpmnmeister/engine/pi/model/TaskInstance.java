package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.Task;

@NoArgsConstructor
public class TaskInstance extends ActivityInstance<Task> {
  public TaskInstance(FLowNodeInstance<?> parentInstance, Task flowNode) {
    super(parentInstance, flowNode);
  }
}

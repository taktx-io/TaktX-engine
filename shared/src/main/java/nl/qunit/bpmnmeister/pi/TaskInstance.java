package nl.qunit.bpmnmeister.pi;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@NoArgsConstructor
public class TaskInstance extends ActivityInstance {
  public TaskInstance(String flowNode, FLowNodeInstance parentInstance) {
    super(flowNode, parentInstance);
  }
}

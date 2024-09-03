package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class ActivityInstance
    extends FLowNodeInstance {

  protected ActivityInstance(String flowNode, FLowNodeInstance parentInstance) {
    super(flowNode, parentInstance);
  }
}

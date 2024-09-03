package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class EventInstance extends FLowNodeInstance {

  protected EventInstance(String flowNode, FLowNodeInstance parentInstance) {
    super(flowNode, parentInstance);
  }
}

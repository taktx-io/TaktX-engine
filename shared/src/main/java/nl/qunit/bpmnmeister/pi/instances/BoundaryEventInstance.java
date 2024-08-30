package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class BoundaryEventInstance extends CatchEventInstance {

  public BoundaryEventInstance(String flowNode) {
    super(flowNode);
  }
}

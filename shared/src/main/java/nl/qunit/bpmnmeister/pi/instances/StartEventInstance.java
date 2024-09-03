package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class StartEventInstance extends CatchEventInstance {

  public StartEventInstance(String flowNode, FLowNodeInstance parentInstance) {
    super(flowNode, parentInstance);
  }
}

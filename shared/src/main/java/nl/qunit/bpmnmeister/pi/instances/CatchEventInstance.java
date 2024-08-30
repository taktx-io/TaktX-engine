package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class CatchEventInstance extends EventInstance {

  protected CatchEventInstance(String flowNode) {
    super(flowNode);
  }
}

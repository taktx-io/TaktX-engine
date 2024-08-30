package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class EndEventInstance extends ThrowEventInstance {

  public EndEventInstance(String flowNode) {
    super(flowNode);
  }
}

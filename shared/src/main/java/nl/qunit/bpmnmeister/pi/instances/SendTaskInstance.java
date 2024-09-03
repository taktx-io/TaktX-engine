package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class SendTaskInstance extends ExternalTaskInstance {

  public SendTaskInstance(String flowNode, FLowNodeInstance parentInstance) {
    super(flowNode, parentInstance);
  }
}

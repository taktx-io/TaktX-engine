package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ServiceTaskInstance extends ExternalTaskInstance {

  public ServiceTaskInstance(String flowNode) {
    super(flowNode);
  }
}

package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;

@NoArgsConstructor
public class ServiceTaskInstance extends ExternalTaskInstance<ServiceTask> {

  public ServiceTaskInstance(FLowNodeInstance<?> parentInstance, ServiceTask flowNode) {
    super(parentInstance, flowNode);
  }
}

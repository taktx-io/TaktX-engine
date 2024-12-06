package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.ServiceTask;

@NoArgsConstructor
public class ServiceTaskInstance extends ExternalTaskInstance<ServiceTask> {

  public ServiceTaskInstance(FLowNodeInstance<?> parentInstance, ServiceTask flowNode) {
    super(parentInstance, flowNode);
  }
}

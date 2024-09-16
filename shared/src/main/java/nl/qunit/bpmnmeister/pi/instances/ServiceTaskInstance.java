package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.ServiceTask2;

@NoArgsConstructor
public class ServiceTaskInstance extends ExternalTaskInstance<ServiceTask2> {

  public ServiceTaskInstance(FLowNodeInstance parentInstance, ServiceTask2 flowNode) {
    super(parentInstance, flowNode);
  }
}

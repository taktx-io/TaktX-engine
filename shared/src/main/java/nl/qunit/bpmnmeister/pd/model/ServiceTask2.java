package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.ServiceTaskInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ServiceTask2 extends ExternalTask2 {

  @Override
  public ActivityInstance newActivityInstance(FLowNodeInstance parentInstance) {
    return new ServiceTaskInstance(this.getId(), parentInstance);
  }
}

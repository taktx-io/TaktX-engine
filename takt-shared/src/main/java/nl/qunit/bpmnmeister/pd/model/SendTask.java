package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.SendTaskInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class SendTask extends ExternalTask {

  @Override
  public ActivityInstance<?> newActivityInstance(FLowNodeInstance<?> parentInstance) {
    return new SendTaskInstance(parentInstance, this);
  }
}

package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.SendTaskInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class SendTask2 extends ExternalTask2 {

  @Override
  public SendTaskInstance newInstance(FLowNodeInstance parentInstance) {
    return new SendTaskInstance(getId(), parentInstance);
  }
}

package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateCatchEventInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class IntermediateCatchEvent2 extends CatchEvent2 {

  @Override
  public FLowNodeInstance newInstance(FLowNodeInstance parentInstance) {
    return new IntermediateCatchEventInstance(parentInstance, this);
  }
}

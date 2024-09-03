package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.StartEventInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class StartEvent2 extends CatchEvent2 {

  public StartEventInstance newInstance(FLowNodeInstance parentInstance) {
    return new StartEventInstance(getId(), parentInstance);
  }
}

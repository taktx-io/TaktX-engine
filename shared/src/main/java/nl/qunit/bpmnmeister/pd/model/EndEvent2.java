package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.EndEventInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class EndEvent2 extends ThrowEvent2 {

  public EndEventInstance newInstance() {
    return new EndEventInstance(getId());
  }
}

package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.StartEventInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class StartEvent2 extends CatchEvent2 {

  public StartEventInstance newInstance() {
    return new StartEventInstance(getId());
  }
}

package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pd.model.IntermediateCatchEvent;

@NoArgsConstructor
@Getter
@Setter
public class IntermediateCatchEventInstance extends CatchEventInstance<IntermediateCatchEvent> {

  public IntermediateCatchEventInstance(
      FLowNodeInstance<?> parentInstance, IntermediateCatchEvent flowNode) {
    super(parentInstance, flowNode);
  }
}

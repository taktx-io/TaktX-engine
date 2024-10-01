package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent2;

@NoArgsConstructor
@Getter
@Setter
public class IntermediateCatchEventInstance extends CatchEventInstance<IntermediateCatchEvent2> {

  public IntermediateCatchEventInstance(
      FLowNodeInstance parentInstance, IntermediateCatchEvent2 flowNode) {
    super(parentInstance, flowNode);
  }
}

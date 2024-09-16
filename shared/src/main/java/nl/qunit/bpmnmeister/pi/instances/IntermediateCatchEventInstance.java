package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent2;

@NoArgsConstructor
public class IntermediateCatchEventInstance extends CatchEventInstance<IntermediateCatchEvent2> {

  public IntermediateCatchEventInstance(
      FLowNodeInstance parentInstance, IntermediateCatchEvent2 flowNode) {
    super(parentInstance, flowNode);
  }
}

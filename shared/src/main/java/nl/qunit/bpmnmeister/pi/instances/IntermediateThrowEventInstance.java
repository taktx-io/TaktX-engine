package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.IntermediateThrowEvent2;

@NoArgsConstructor
public class IntermediateThrowEventInstance extends ThrowEventInstance<IntermediateThrowEvent2> {

  public IntermediateThrowEventInstance(
      FLowNodeInstance parentInstance, IntermediateThrowEvent2 flowNode) {
    super(parentInstance, flowNode);
  }
}

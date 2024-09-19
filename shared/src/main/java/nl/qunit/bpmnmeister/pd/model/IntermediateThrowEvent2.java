package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateThrowEventInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class IntermediateThrowEvent2 extends ThrowEvent2 {

  @Override
  public FLowNodeInstance newInstance(
      FLowNodeInstance parentInstance, FlowNodeStates2 flowNodeStates) {
    return new IntermediateThrowEventInstance(parentInstance, this);
  }
}

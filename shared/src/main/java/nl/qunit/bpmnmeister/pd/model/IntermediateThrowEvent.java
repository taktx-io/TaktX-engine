package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateThrowEventInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class IntermediateThrowEvent extends ThrowEvent {

  @Override
  public FLowNodeInstance<?> newInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    return new IntermediateThrowEventInstance(parentInstance, this);
  }
}

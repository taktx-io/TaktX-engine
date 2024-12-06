package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstances;
import nl.qunit.bpmnmeister.engine.pi.model.IntermediateThrowEventInstance;

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

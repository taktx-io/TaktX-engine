package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.BoundaryEventInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstances;

@Getter
@SuperBuilder
public class BoundaryEvent extends CatchEvent {
  private String attachedToRef;
  @Setter private FlowNode attachedActivity;

  private boolean cancelActivity;

  @Override
  public BoundaryEventInstance newInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    return new BoundaryEventInstance(parentInstance, this);
  }
}

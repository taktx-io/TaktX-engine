package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.instances.BoundaryEventInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

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

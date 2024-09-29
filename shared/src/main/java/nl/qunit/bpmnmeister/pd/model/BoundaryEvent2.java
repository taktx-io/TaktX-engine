package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.instances.BoundaryEventInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@SuperBuilder
public class BoundaryEvent2 extends CatchEvent2 {
  private String attachedToRef;
  @Setter private FlowNode2 attachedActivity;

  private boolean cancelActivity;

  @Override
  public BoundaryEventInstance newInstance(
      FLowNodeInstance parentInstance, FlowNodeStates2 flowNodeStates) {
    return new BoundaryEventInstance(parentInstance, this);
  }
}

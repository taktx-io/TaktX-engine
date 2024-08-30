package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.BoundaryEventInstance;

@Getter
@SuperBuilder
public class BoundaryEvent2 extends CatchEvent2 {
  private String attachedToRef;
  private boolean cancelActivity;

  @Override
  public BoundaryEventInstance newInstance() {
    return new BoundaryEventInstance(getId());
  }
}

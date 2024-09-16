package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent2;

@NoArgsConstructor
public class BoundaryEventInstance extends CatchEventInstance<BoundaryEvent2> {

  public BoundaryEventInstance(FLowNodeInstance parentInstance, BoundaryEvent2 flowNode) {
    super(parentInstance, flowNode);
  }
}

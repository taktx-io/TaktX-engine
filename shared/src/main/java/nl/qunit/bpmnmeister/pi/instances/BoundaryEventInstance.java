package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent2;

@NoArgsConstructor
public class BoundaryEventInstance extends CatchEventInstance<BoundaryEvent2> {

  public BoundaryEventInstance(FLowNodeInstance parentInstance, BoundaryEvent2 flowNode) {
    super(parentInstance, flowNode);
  }


  @Override
  public boolean stateAllowsStart() {
    return true;
  }

  @Override
  public boolean stateAllowsContinue() {
    return false;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return true;
  }

  @Override
  public boolean isNotAwaiting() {
    return true;
  }

  @Override
  public boolean isCompleted() {
    return true;
  }

  @Override
  public void terminate() {
    // Do nothing
  }
}

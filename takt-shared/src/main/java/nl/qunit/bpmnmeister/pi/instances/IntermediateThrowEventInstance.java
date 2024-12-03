package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.IntermediateThrowEvent;

@NoArgsConstructor
public class IntermediateThrowEventInstance extends ThrowEventInstance<IntermediateThrowEvent> {

  public IntermediateThrowEventInstance(
      FLowNodeInstance<?> parentInstance, IntermediateThrowEvent flowNode) {
    super(parentInstance, flowNode);
  }

  @Override
  public boolean stateAllowsStart() {
    return true;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return false;
  }

  @Override
  public boolean stateAllowsContinue() {
    return false;
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

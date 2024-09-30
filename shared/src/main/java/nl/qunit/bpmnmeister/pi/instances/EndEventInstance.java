package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.EndEvent2;

@NoArgsConstructor
public class EndEventInstance extends ThrowEventInstance<EndEvent2> {

  public EndEventInstance(FLowNodeInstance<?> parentInstance, EndEvent2 flowNode) {
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

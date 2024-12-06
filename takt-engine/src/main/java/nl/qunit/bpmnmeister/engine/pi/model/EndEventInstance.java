package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.EndEvent;

@NoArgsConstructor
public class EndEventInstance extends ThrowEventInstance<EndEvent> {

  public EndEventInstance(FLowNodeInstance<?> parentInstance, EndEvent flowNode) {
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

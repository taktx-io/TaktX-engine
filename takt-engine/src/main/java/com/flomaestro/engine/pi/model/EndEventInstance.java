package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.EndEvent;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class EndEventInstance extends ThrowEventInstance<EndEvent> {

  public EndEventInstance(FlowNodeInstance<?> parentInstance, EndEvent flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
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

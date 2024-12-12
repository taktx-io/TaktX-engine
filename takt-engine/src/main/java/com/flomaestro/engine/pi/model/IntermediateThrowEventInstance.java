package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.IntermediateThrowEvent;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class IntermediateThrowEventInstance extends ThrowEventInstance<IntermediateThrowEvent> {

  public IntermediateThrowEventInstance(
      FlowNodeInstance<?> parentInstance, IntermediateThrowEvent flowNode) {
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

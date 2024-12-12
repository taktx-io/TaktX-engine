package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.StartEvent;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class StartEventInstance extends CatchEventInstance<StartEvent> {

  public StartEventInstance(FlowNodeInstance<?> parentInstance, StartEvent flowNode) {
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

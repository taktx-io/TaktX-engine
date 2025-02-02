package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.Event;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class EventInstance<N extends Event> extends FlowNodeInstance<N> {

  protected EventInstance(FlowNodeInstance<?> parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return isCompleted();
  }

  @Override
  public boolean isAwaiting() {
    return false;
  }

  @Override
  public void setStartedState() {
    // Do nothing
  }

  @Override
  public void setInitialState() {}

  @Override
  public boolean wasAwaiting() {
    return false;
  }

  @Override
  public boolean wasNew() {
    return true;
  }

  @Override
  public boolean stateChanged() {
    return false;
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return isCompleted();
  }
}

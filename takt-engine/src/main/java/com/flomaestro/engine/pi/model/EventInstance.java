package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.Event;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class EventInstance<N extends Event> extends FlowNodeInstance<N> {

  protected EventInstance(FlowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return isCompleted();
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return isCompleted();
  }
}

package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ThrowEvent;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class ThrowEventInstance<N extends ThrowEvent> extends EventInstance<N> {

  protected ThrowEventInstance(
      FlowNodeInstance<?> parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }
}

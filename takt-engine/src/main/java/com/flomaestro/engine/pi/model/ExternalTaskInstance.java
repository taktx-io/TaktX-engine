package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ExternalTask;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ExternalTaskInstance<N extends ExternalTask> extends ActivityInstance<N> {
  private int attempt;

  public ExternalTaskInstance(FlowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }

  public int increaseAttempt() {
    return ++attempt;
  }
}

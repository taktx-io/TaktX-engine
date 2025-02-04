package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.BoundaryEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class BoundaryEventInstance extends CatchEventInstance<BoundaryEvent> {
  private long attachedInstanceId;

  public BoundaryEventInstance(FlowNodeInstance<?> parentInstance, BoundaryEvent flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return false;
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return isCompleted() || !getFlowNode().isCancelActivity();
  }
}

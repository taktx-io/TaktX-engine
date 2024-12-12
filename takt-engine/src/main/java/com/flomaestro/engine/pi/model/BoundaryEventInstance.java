package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.BoundaryEvent;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class BoundaryEventInstance extends CatchEventInstance<BoundaryEvent> {
  private UUID attachedInstanceId;

  public BoundaryEventInstance(FlowNodeInstance<?> parentInstance, BoundaryEvent flowNode) {
    super(parentInstance, flowNode);
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

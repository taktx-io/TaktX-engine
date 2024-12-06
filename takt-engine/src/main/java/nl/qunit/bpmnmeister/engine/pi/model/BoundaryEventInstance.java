package nl.qunit.bpmnmeister.engine.pi.model;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pd.model.BoundaryEvent;

@NoArgsConstructor
@Getter
@Setter
public class BoundaryEventInstance extends CatchEventInstance<BoundaryEvent> {
  private UUID attachedInstanceId;

  public BoundaryEventInstance(FLowNodeInstance<?> parentInstance, BoundaryEvent flowNode) {
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

package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent2;

@NoArgsConstructor
public class BoundaryEventInstance extends CatchEventInstance<BoundaryEvent2> {
  @Setter @Getter private UUID attachedInstanceId;

  public BoundaryEventInstance(FLowNodeInstance parentInstance, BoundaryEvent2 flowNode) {
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

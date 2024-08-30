package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class ActivityInstance
    extends FLowNodeInstance {
  private UUID parentElementInstanceId;
  private int loopCnt;

  protected ActivityInstance(String flowNode) {
    super(flowNode);
  }
}

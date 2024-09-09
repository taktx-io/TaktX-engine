package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public abstract class ActivityInstance extends FLowNodeInstance {
  private int loopCnt;

  protected ActivityInstance(String flowNode, FLowNodeInstance parentInstance) {
    super(flowNode, parentInstance);
  }

  public void increaseLoopCnt() {
    loopCnt++;
  }
}

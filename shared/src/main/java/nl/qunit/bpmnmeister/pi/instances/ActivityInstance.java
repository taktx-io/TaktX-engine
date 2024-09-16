package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;

@NoArgsConstructor
@Setter
@Getter
public abstract class ActivityInstance<N extends FlowNode2> extends FLowNodeInstance<N> {
  private int loopCnt;

  protected ActivityInstance(FLowNodeInstance parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }

  public void increaseLoopCnt() {
    loopCnt++;
  }
}

package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public abstract class ActivityState extends FlowNodeState {

  private final FlowNodeStateEnum state;
  private final UUID parentElementInstanceId;
  private final int loopCnt;

  protected ActivityState(
      FlowNodeStateEnum state,
      String elementId,
      UUID parentElementInstanceId,
      UUID elementInstanceId,
      int passedCnt,
      int loopCnt,
      String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, state, inputFlowId);
    this.state = state;
    this.parentElementInstanceId = parentElementInstanceId;
    this.loopCnt = loopCnt;
  }
}

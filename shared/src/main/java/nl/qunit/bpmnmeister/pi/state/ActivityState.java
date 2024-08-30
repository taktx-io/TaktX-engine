package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public abstract class ActivityState extends FlowNodeStateDTO {

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

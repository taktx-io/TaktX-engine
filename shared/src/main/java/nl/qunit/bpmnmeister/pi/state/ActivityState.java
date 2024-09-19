package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public abstract class ActivityState extends FlowNodeStateDTO {

  private final ActtivityStateEnum state;
  private final int loopCnt;

  protected ActivityState(
      ActtivityStateEnum state,
      String elementId,
      UUID elementInstanceId,
      int passedCnt,
      int loopCnt,
      String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, inputFlowId);
    this.state = state;
    this.loopCnt = loopCnt;
  }

  @Override
  public boolean isTerminated() {
    return state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public boolean isFailed() {
    return state == ActtivityStateEnum.FAILED;
  }
}

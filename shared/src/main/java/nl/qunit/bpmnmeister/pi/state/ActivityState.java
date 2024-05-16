package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public abstract class ActivityState extends FlowNodeState {

  private final FlowNodeStateEnum state;
  private final int loopCnt;

  protected ActivityState(
      FlowNodeStateEnum state,
      UUID elementInstanceId,
      int passedCnt,
      int loopCnt,
      String inputFlowId) {
    super(elementInstanceId, passedCnt, state, inputFlowId);
    this.state = state;
    this.loopCnt = loopCnt;
  }

  @JsonIgnore
  public abstract ActivityState getNextLoopState();

  @JsonIgnore
  public abstract ActivityState getFinishedLoopState();
}

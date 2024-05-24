package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class CallActivityState extends ActivityState {
  @JsonCreator
  public CallActivityState(
      @JsonProperty("state") FlowNodeStateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("inputFlowId") String inputFlowId) {
    super(state, elementInstanceId, passedCnt, loopCnt, inputFlowId);
  }

  @Override
  public ActivityState getNextLoopState() {
    return new CallActivityState(
        FlowNodeStateEnum.ACTIVE,
        this.getElementInstanceId(),
        this.getPassedCnt(),
        this.getLoopCnt() + 1,
        this.getInputFlowId());
  }

  @Override
  public ActivityState getFinishedLoopState() {
    return new CallActivityState(
        FlowNodeStateEnum.FINISHED,
        this.getElementInstanceId(),
        this.getPassedCnt() + 1,
        this.getLoopCnt() + 1,
        this.getInputFlowId());
  }
}

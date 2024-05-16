package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class TaskState extends ActivityState {
  @JsonCreator
  public TaskState(
      @JsonProperty("state") FlowNodeStateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("inputFlowId") String inputFlowId) {
    super(state, elementInstanceId, passedCnt, loopCnt, inputFlowId);
  }

  @Override
  public ActivityState getNextLoopState() {
    return new TaskState(
        FlowNodeStateEnum.ACTIVE,
        this.getElementInstanceId(),
        this.getPassedCnt(),
        this.getLoopCnt() + 1,
        this.getInputFlowId());
  }

  @Override
  public ActivityState getFinishedLoopState() {
    return new TaskState(
        FlowNodeStateEnum.FINISHED,
        this.getElementInstanceId(),
        this.getPassedCnt() + 1,
        this.getLoopCnt() + 1,
        this.getInputFlowId());
  }
}

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
      @JsonProperty("state") ActivityStateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt) {
    super(state, elementInstanceId, passedCnt, loopCnt);
  }

  @Override
  public ActivityState getNextLoopState() {
    return new TaskState(
        ActivityStateEnum.ACTIVE,
        this.getElementInstanceId(),
        this.getPassedCnt(),
        this.getLoopCnt() + 1);
  }

  @Override
  public ActivityState getFinishedLoopState() {
    return new TaskState(
        ActivityStateEnum.FINISHED,
        this.getElementInstanceId(),
        this.getPassedCnt() + 1,
        this.getLoopCnt() + 1);
  }

  @Override
  public BpmnElementState terminate() {
    if (this.getState() == ActivityStateEnum.ACTIVE) {
      return new TaskState(
          ActivityStateEnum.TERMINATED, this.getElementInstanceId(), this.getPassedCnt(), this.getLoopCnt());
    } else {
      return this;
    }
  }
}

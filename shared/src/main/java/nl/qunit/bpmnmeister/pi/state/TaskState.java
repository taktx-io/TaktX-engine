package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;

@Getter
public class TaskState extends ActivityState {
  @JsonCreator
  public TaskState(
      @JsonProperty("state") ActivityStateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt) {
    super(state, elementInstanceId, passedCnt);
  }

  @Override
  public String toString() {
    return "TaskState{"
        + "elementInstanceId="
        + getElementInstanceId()
        + ", state="
        + getState()
        + ", passedCnt="
        + getPassedCnt()
        + '}';
  }
}

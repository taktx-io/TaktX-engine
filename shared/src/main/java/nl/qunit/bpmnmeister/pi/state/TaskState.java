package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public class TaskState extends ActivityState {
  @JsonCreator
  public TaskState(
      @JsonProperty("state") FlowNodeStateEnum state,
      @JsonProperty("parentElementInstanceId") UUID parentElementInstanceId,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("inputFlowId") String inputFlowId) {
    super(
        state,
        elementId,
        parentElementInstanceId,
        elementInstanceId,
        passedCnt,
        loopCnt,
        inputFlowId);
  }
}

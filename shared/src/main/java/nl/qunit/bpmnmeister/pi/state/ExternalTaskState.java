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
public class ExternalTaskState extends TaskState {
  private final int attempt;

  @JsonCreator
  public ExternalTaskState(
      @JsonProperty("state") FlowNodeStateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("inputFlowId") String inputFlowId,
      @JsonProperty("attempt") int attempt) {
    super(state, elementInstanceId, elementId, passedCnt, loopCnt, inputFlowId);
    this.attempt = attempt;
  }
}

package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public class ReceiveTaskState extends TaskState {

  @JsonCreator
  public ReceiveTaskState(
      @Nonnull @JsonProperty("state") FlowNodeStateEnum state,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("inputFlowId") String inputflowId) {
    super(
        state,
        elementInstanceId,
        elementId,
        passedCnt,
        loopCnt,
        inputflowId);
  }
}

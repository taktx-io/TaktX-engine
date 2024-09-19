package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder(toBuilder = true)
public class CallActivityState extends ActivityState {

  private final UUID childProcessInstanceId;

  @JsonCreator
  public CallActivityState(
      @Nonnull @JsonProperty("state") ActtivityStateEnum state,
      @Nonnull @JsonProperty("childProcessInstanceId") UUID childProcessInstanceId,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId) {
    super(state, elementId, elementInstanceId, passedCnt, loopCnt, inputFlowId);
    this.childProcessInstanceId = childProcessInstanceId;
  }
}

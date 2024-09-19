package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeStatesDTO;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public class SubProcessState extends ActivityState {

  private final FlowNodeStatesDTO flowNodeStates;

  @JsonCreator
  public SubProcessState(
      @Nonnull @JsonProperty("flowNodeStates") FlowNodeStatesDTO flowNodeStates,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("state") ActtivityStateEnum state,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("inputFlowId") String inputFlowId) {
    super(state, elementId, elementInstanceId, passedCnt, loopCnt, inputFlowId);
    this.flowNodeStates = flowNodeStates;
  }
}

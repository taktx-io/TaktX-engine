package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.UUID;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeStatesDTO;

@Getter
@SuperBuilder(toBuilder = true)
public class MultiInstanceState extends ActivityState implements WithFlowNodeStatesDTO {
  private FlowNodeStatesDTO flowNodeStates;

  @JsonCreator
  public MultiInstanceState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @Nonnull @JsonProperty("state") ActtivityStateEnum state,
      @Nonnull @JsonProperty("flowNodeStates") FlowNodeStatesDTO flowNodeStates) {
    super(state, elementId, elementInstanceId, passedCnt, loopCnt, new HashSet<>());
    this.flowNodeStates = flowNodeStates;
  }
}

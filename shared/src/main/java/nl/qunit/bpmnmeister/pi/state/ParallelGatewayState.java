package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ParallelGatewayState extends GatewayState {
  Set<String> triggeredFlows;

  @JsonCreator
  public ParallelGatewayState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("triggeredFlows") Set<String> triggeredFlows,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("state") FlowNodeStateEnum state,
      @JsonProperty("inputFlowId") String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, state, inputFlowId);
    this.triggeredFlows = triggeredFlows;
  }
}

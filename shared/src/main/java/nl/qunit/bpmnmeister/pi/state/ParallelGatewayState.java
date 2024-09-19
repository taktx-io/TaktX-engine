package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public class ParallelGatewayState extends GatewayState {
  Set<String> triggeredFlows;

  @JsonCreator
  public ParallelGatewayState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("triggeredFlows") Set<String> triggeredFlows,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("inputFlowId") String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, inputFlowId);
    this.triggeredFlows = triggeredFlows;
  }
}

package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public class ParallelGatewayInstanceDTO extends GatewayInstanceDTO {
  Set<String> triggeredFlows;

  @JsonCreator
  public ParallelGatewayInstanceDTO(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("triggeredFlows") Set<String> triggeredFlows,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("selectedOutputFlows") Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt, selectedOutputFlows);
    this.triggeredFlows = triggeredFlows;
  }
}

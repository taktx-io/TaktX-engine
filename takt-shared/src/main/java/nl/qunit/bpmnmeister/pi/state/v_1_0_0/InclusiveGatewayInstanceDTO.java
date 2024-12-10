package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public class InclusiveGatewayInstanceDTO extends GatewayInstanceDTO {

  private final Set<String> triggeredInputFlows;

  @JsonCreator
  public InclusiveGatewayInstanceDTO(
      @Nonnull @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("triggeredInputFlows") Set<String> triggeredInputFlows,
      @Nonnull @JsonProperty("selectedOutputFlows") Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt, selectedOutputFlows);
    this.triggeredInputFlows = triggeredInputFlows;
  }
}

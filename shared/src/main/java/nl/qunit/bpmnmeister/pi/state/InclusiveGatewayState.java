package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public class InclusiveGatewayState extends GatewayState {

  private final Set<String> triggeredInputFlows;
  private final Set<String> selectedOutputFlows;

  @JsonCreator
  public InclusiveGatewayState(
      @Nonnull @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") FlowNodeStateEnum state,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId,
      @Nonnull @JsonProperty("triggeInputFlows") Set<String> triggeredInputFlows,
      @Nonnull @JsonProperty("selectedOutputFlows") Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt, state, inputFlowId);
    this.triggeredInputFlows = triggeredInputFlows;
    this.selectedOutputFlows = selectedOutputFlows;
  }
}

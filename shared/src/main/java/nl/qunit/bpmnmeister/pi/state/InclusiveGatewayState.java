package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;

@Getter
public class InclusiveGatewayState extends GatewayState {

  private final Set<String> triggeredInputFlows;
  private final Set<String> selectedOutputFlows;

  @JsonCreator
  public InclusiveGatewayState(
      @Nonnull @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("state") FlowNodeStateEnum state,
      @JsonProperty("inputFlowId") String inputFlowId,
      @JsonProperty("triggeInputFlows") Set<String> triggeredInputFlows,
      @JsonProperty("selectedOutputFlows") Set<String> selectedOutputFlows) {
    super(elementInstanceId, passedCnt, state, inputFlowId);
    this.triggeredInputFlows = triggeredInputFlows;
    this.selectedOutputFlows = selectedOutputFlows;
  }
}

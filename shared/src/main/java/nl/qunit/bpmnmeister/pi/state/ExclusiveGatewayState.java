package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public class ExclusiveGatewayState extends GatewayState {
  @JsonCreator
  public ExclusiveGatewayState(
      @Nonnull @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("state") FlowNodeStateEnum state,
      @JsonProperty("inputFlowId") String inputFlowId) {
    super(elementInstanceId, passedCnt, state, inputFlowId);
  }
}

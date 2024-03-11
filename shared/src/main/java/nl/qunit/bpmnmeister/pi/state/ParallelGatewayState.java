package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;

@Getter
public class ParallelGatewayState extends GatewayState {
  Set<BaseElementId> triggeredFlows;

  @JsonCreator
  public ParallelGatewayState(
      @JsonProperty("state") StateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("triggeredFlows") Set<BaseElementId> triggeredFlows) {
    super(state, elementInstanceId);
    this.triggeredFlows = triggeredFlows;
  }
}

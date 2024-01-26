package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class ParallelGatewayState extends BpmnElementState {
  Set<String> triggeredFlows;

  @JsonCreator
  public ParallelGatewayState(
      @JsonProperty("state") StateEnum state,
      @JsonProperty("triggeredFlows") Set<String> triggeredFlows) {
    super(state);
    this.triggeredFlows = triggeredFlows;
  }
}

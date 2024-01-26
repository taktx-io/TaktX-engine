package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
@Builder
public class TriggerResult {
  private final BpmnElementState newElementState;
  private final Set<String> newActiveFlows;

  @JsonCreator
  public TriggerResult(
      @JsonProperty("newElementState") BpmnElementState newElementState,
      @JsonProperty("newActiveFlows") Set<String> newActiveFlows) {
    this.newElementState = newElementState;
    this.newActiveFlows = newActiveFlows;
  }
}

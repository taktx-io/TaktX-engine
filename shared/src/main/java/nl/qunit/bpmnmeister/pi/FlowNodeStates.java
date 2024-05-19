package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@Getter
@ToString
public class FlowNodeStates {

  public static final FlowNodeStates EMPTY = new FlowNodeStates(Map.of());
  private final Map<String, FlowNodeState> elementStateMap;

  @JsonCreator
  public FlowNodeStates(
      @JsonProperty("flowNodeStates") Map<String, FlowNodeState> elementStateMap) {
    this.elementStateMap = elementStateMap;
  }

  public Optional<FlowNodeState> get(String elementId) {
    return Optional.ofNullable(elementStateMap.get(elementId));
  }

  public FlowNodeStates put(String elementId, FlowNodeState newElementState) {
    Map<String, FlowNodeState> states = new HashMap<>(elementStateMap);
    states.put(elementId, newElementState);
    return new FlowNodeStates(Map.copyOf(states));
  }
}

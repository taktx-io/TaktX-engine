package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

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

  public FlowNodeStates put(String elementId, @Nonnull FlowNodeState newElementState) {
    Map<String, FlowNodeState> states = new HashMap<>(elementStateMap);
    states.put(elementId, newElementState);
    return new FlowNodeStates(Map.copyOf(states));
  }

  public List<FlowNodeState> getWithState(FlowNodeStateEnum flowNodeStateEnum) {
    return elementStateMap.values().stream()
        .filter(state -> state.getState() == flowNodeStateEnum)
        .toList();
  }
}

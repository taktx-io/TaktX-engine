package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
@ToString
public class FlowNodeStates {

  public static final FlowNodeStates EMPTY = new FlowNodeStates(Map.of());
  private final Map<UUID, FlowNodeState> elementStateMap;

  @JsonCreator
  public FlowNodeStates(
      @JsonProperty("flowNodeStates") Map<UUID, FlowNodeState> elementStateMap) {
    this.elementStateMap = elementStateMap;
  }

  @JsonIgnore
  public Optional<FlowNodeState> get(UUID elementInstanceId) {
    return Optional.ofNullable(elementStateMap.get(elementInstanceId));
  }

  @JsonIgnore
  public List<FlowNodeState> filter(Predicate<FlowNodeState> predicate) {
    return elementStateMap.values().stream().filter(predicate).toList();
  }

  @JsonIgnore
  public List<FlowNodeState> get(String elementId) {
    return elementStateMap.values().stream().filter(state -> state.getElementId().equals(elementId)).toList();
  }

  @JsonIgnore
  public FlowNodeStates put(@Nonnull FlowNodeState newElementState) {
    Map<UUID, FlowNodeState> states = new HashMap<>(elementStateMap);
    states.put(newElementState.getElementInstanceId(), newElementState);
    return new FlowNodeStates(Map.copyOf(states));
  }

  @JsonIgnore
  public FlowNodeStates putAll(@Nonnull List<FlowNodeState> newElementState) {
    Map<UUID, FlowNodeState> states = new HashMap<>(elementStateMap);
    newElementState.forEach(state -> states.put(state.getElementInstanceId(), state));
    return new FlowNodeStates(Map.copyOf(states));
  }

  @JsonIgnore
  public List<FlowNodeState> getWithState(FlowNodeStateEnum flowNodeStateEnum) {
    return elementStateMap.values().stream()
        .filter(state -> state.getState() == flowNodeStateEnum)
        .toList();
  }
}

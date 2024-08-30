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
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
@ToString
public class FlowNodeStates {

  public static final FlowNodeStates EMPTY = new FlowNodeStates(Map.of());
  private final Map<UUID, FlowNodeStateDTO> elementStateMap;

  @JsonCreator
  public FlowNodeStates(
      @JsonProperty("flowNodeStates") Map<UUID, FlowNodeStateDTO> elementStateMap) {
    this.elementStateMap = elementStateMap;
  }

  @JsonIgnore
  public Optional<FlowNodeStateDTO> get(UUID elementInstanceId) {
    return Optional.ofNullable(elementStateMap.get(elementInstanceId));
  }

  @JsonIgnore
  public List<FlowNodeStateDTO> filter(Predicate<FlowNodeStateDTO> predicate) {
    return elementStateMap.values().stream().filter(predicate).toList();
  }

  @JsonIgnore
  public List<FlowNodeStateDTO> get(String elementId) {
    return elementStateMap.values().stream()
        .filter(state -> state.getElementId().equals(elementId))
        .toList();
  }

  @JsonIgnore
  public FlowNodeStates put(@Nonnull FlowNodeStateDTO newElementState) {
    Map<UUID, FlowNodeStateDTO> states = new HashMap<>(elementStateMap);
    states.put(newElementState.getElementInstanceId(), newElementState);
    return new FlowNodeStates(Map.copyOf(states));
  }

  @JsonIgnore
  public FlowNodeStates putAll(@Nonnull List<FlowNodeStateDTO> newElementState) {
    Map<UUID, FlowNodeStateDTO> states = new HashMap<>(elementStateMap);
    newElementState.forEach(state -> states.put(state.getElementInstanceId(), state));
    return new FlowNodeStates(Map.copyOf(states));
  }

  @JsonIgnore
  public List<FlowNodeStateDTO> getWithState(FlowNodeStateEnum flowNodeStateEnum) {
    return elementStateMap.values().stream()
        .filter(state -> state.getState() == flowNodeStateEnum)
        .toList();
  }
}

package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
public class ElementStates {

  public static final ElementStates EMPTY = new ElementStates(Map.of());
  private final Map<String, BpmnElementState> elementStateMap;

  @JsonCreator
  public ElementStates(
      @JsonProperty("elementStates") Map<String, BpmnElementState> elementStateMap) {
    this.elementStateMap = elementStateMap;
  }

  public BpmnElementState get(String elementId) {
    return elementStateMap.get(elementId);
  }

  public ElementStates put(String elementId, BpmnElementState newElementState) {
    Map<String, BpmnElementState> states = new HashMap<>(elementStateMap);
    states.put(elementId, newElementState);
    return new ElementStates(Map.copyOf(states));
  }

  @Override
  public String toString() {
    return "ElementStates{" + "elementStateMap=" + elementStateMap + '}';
  }

  public ElementStates terminate() {
    Map<String, BpmnElementState> terminatedStates = elementStateMap.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().terminate()));
    return new ElementStates(terminatedStates);
  }
}

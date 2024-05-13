package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
@ToString
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
}

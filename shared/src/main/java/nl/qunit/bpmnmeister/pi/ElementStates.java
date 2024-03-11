package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

public class ElementStates {

  public static final ElementStates EMPTY = new ElementStates(Map.of());
  private final Map<BaseElementId, BpmnElementState> elementStateMap;

  @JsonCreator
  public ElementStates(
      @JsonProperty("elementStates") Map<BaseElementId, BpmnElementState> elementStateMap) {
    this.elementStateMap = elementStateMap;
  }

  @JsonIgnore
  public BpmnElementState get(BaseElementId elementId) {
    return elementStateMap.get(elementId);
  }

  public ElementStates put(BaseElementId elementId, BpmnElementState newElementState) {
    Map<BaseElementId, BpmnElementState> states = new HashMap<>(elementStateMap);
    states.put(elementId, newElementState);
    return new ElementStates(Map.copyOf(states));
  }
}

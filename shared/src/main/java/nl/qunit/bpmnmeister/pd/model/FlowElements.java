package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FlowElements {
  public static FlowElements EMPTY = new FlowElements(Map.of());

  private final Map<BaseElementId, BaseElement> elements;

  @JsonCreator
  public FlowElements(Map<BaseElementId, BaseElement> elements) {
    this.elements = elements;
  }

  @JsonIgnore
  public List<BaseElement> values() {
    return List.copyOf(elements.values());
  }

  @JsonIgnore
  public Set<BaseElementId> keySet() {
    return Set.copyOf(elements.keySet());
  }

  @JsonIgnore
  public BaseElement get(BaseElementId id) {
    return elements.get(id);
  }

  @JsonIgnore
  public List<StartEvent> getStartEvents() {
    return elements.values().stream()
        .filter(Process.class::isInstance)
        .map(Process.class::cast)
        .flatMap(process -> process.getFlowElements().values().stream())
        .filter(StartEvent.class::isInstance)
        .map(StartEvent.class::cast)
        .toList();
  }

  @JsonIgnore
  public Optional<BaseElement> getFlowElement(BaseElementId id) {
    return elements.values().stream()
        .filter(Process.class::isInstance)
        .map(Process.class::cast)
        .flatMap(process -> process.getFlowElements().values().stream())
        .filter(flowElement -> id.equals(flowElement.getId()))
        .findFirst();
  }
}

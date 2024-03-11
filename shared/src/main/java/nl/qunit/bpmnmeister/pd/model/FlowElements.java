package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;

@Getter
public class FlowElements {
  public static FlowElements EMPTY = new FlowElements(Map.of());

  private final Map<BaseElementId, FlowElement> elements;

  @JsonCreator
  public FlowElements(
      @Nonnull @JsonProperty("elements") Map<BaseElementId, FlowElement> elements) {
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
  public FlowElement get(BaseElementId id) {
    return elements.get(id);
  }

  @JsonIgnore
  public List<StartEvent> getStartEvents() {
    return elements.values().stream()
        .filter(StartEvent.class::isInstance)
        .map(StartEvent.class::cast)
        .toList();
  }

  @JsonIgnore
  public Optional<FlowElement> getFlowElement(BaseElementId id) {
    return elements.values().stream()
        .filter(flowElement -> id.equals(flowElement.getId()))
        .findFirst();
  }
}

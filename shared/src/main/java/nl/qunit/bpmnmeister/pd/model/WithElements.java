package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WithElements {
  Map<BaseElementId, BaseElement> getElements();

  @JsonIgnore
  default List<StartEvent> getStartEvents() {
    return getElements().values().stream()
        .filter(Process.class::isInstance)
        .map(Process.class::cast)
        .flatMap(process -> process.getFlowElements().values().stream())
        .filter(StartEvent.class::isInstance)
        .map(StartEvent.class::cast)
        .toList();
  }

  @JsonIgnore
  default Optional<FlowElement> getFlowElement(BaseElementId id) {
    return getElements().values().stream()
        .filter(Process.class::isInstance)
        .map(Process.class::cast)
        .flatMap(process -> process.getFlowElements().values().stream())
        .filter(flowElement -> id.equals(flowElement.getId()))
        .findFirst();
  }
}

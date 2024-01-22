package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

@Builder
@Getter
@EqualsAndHashCode
public class ProcessDefinition {
  private final Definitions definitions;
  private final Integer version;

  @JsonCreator
  public ProcessDefinition(
      @JsonProperty("definitions") Definitions definitions,
      @JsonProperty("id") Integer version) {
    this.definitions = definitions;
    this.version = version;
  }

  @Override
  public String toString() {
    return "ProcessDefinition{" +
            "definitions=" + definitions +
            ", version=" + version +
            '}';
  }

  @JsonIgnore
  public List<StartEvent> getStartEvents() {
    return definitions.getElements().values().stream()
            .filter(Process.class::isInstance)
            .map(Process.class::cast)
            .flatMap(process -> process.getFlowElements().values().stream())
            .filter(StartEvent.class::isInstance)
            .map(StartEvent.class::cast)
            .toList();
  }

  @JsonIgnore
  public Optional<FlowElement> getFlowElement(String id) {
    return definitions.getElements().values().stream()
            .filter(Process.class::isInstance)
            .map(Process.class::cast)
            .flatMap(process -> process.getFlowElements().values().stream())
            .filter(flowElement -> id.equals(flowElement.getId()))
            .findFirst();
  }
}

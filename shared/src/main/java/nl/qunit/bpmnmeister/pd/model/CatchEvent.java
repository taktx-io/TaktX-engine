package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public abstract class CatchEvent extends Event {
  protected final Set<EventDefinition> eventDefinitions;

  protected CatchEvent(
      Set<EventDefinition> eventDefinitions,
      String id,
      Set<String> incoming,
      Set<String> outgoing) {
    super(id, incoming, outgoing);
    this.eventDefinitions = eventDefinitions;
  }


  @JsonIgnore
  public Set<TimerEventDefinition> getTimerEventDefinitions() {
    return eventDefinitions.stream()
            .filter(TimerEventDefinition.class::isInstance)
            .map(TimerEventDefinition.class::cast)
            .collect(Collectors.toSet());
  }
}

package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class CatchEvent extends Event implements WithIoMapping {
  private Set<EventDefinition> eventDefinitions;

  public Set<TimerEventDefinition> getTimerEventDefinitions() {
    return eventDefinitions.stream()
        .filter(TimerEventDefinition.class::isInstance)
        .map(TimerEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }

  public Set<MessageEventDefinition> getMessageventDefinitions() {
    return eventDefinitions.stream()
        .filter(MessageEventDefinition.class::isInstance)
        .map(MessageEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }

  public Set<LinkEventDefinition> getLinkventDefinitions() {
    return eventDefinitions.stream()
        .filter(LinkEventDefinition.class::isInstance)
        .map(LinkEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }
}

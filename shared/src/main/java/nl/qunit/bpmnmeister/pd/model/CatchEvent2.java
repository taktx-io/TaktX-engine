package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class CatchEvent2 extends Event2 implements WithIoMapping {
  private Set<EventDefinition2> eventDefinitions;

  public Set<TimerEventDefinition2> getTimerEventDefinitions() {
    return eventDefinitions.stream()
        .filter(TimerEventDefinition2.class::isInstance)
        .map(TimerEventDefinition2.class::cast)
        .collect(Collectors.toSet());
  }

  public Set<MessageEventDefinition2> getMessageventDefinitions() {
    return eventDefinitions.stream()
        .filter(MessageEventDefinition2.class::isInstance)
        .map(MessageEventDefinition2.class::cast)
        .collect(Collectors.toSet());
  }

  public Set<LinkEventDefinition2> getLinkventDefinitions() {
    return eventDefinitions.stream()
        .filter(LinkEventDefinition2.class::isInstance)
        .map(LinkEventDefinition2.class::cast)
        .collect(Collectors.toSet());
  }
}

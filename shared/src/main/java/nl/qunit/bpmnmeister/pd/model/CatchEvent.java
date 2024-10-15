package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class CatchEvent extends Event
    implements WithIoMapping, WithEscalationEventDefinitions, WithErrorEventDefinitions {
  private Set<EventDefinition> eventDefinitions;

  public Set<TimerEventDefinition> getTimerEventDefinitions() {
    return getDefinitions(TimerEventDefinition.class);
  }

  public Set<MessageEventDefinition> getMessageventDefinitions() {
    return getDefinitions(MessageEventDefinition.class);
  }

  public Set<LinkEventDefinition> getLinkventDefinitions() {
    return getDefinitions(LinkEventDefinition.class);
  }

  public Set<TerminateEventDefinition> getTerminateEventDefinitions() {
    return getDefinitions(TerminateEventDefinition.class);
  }

  public Set<EscalationEventDefinition> getEscalationEventDefinitions() {
    return getDefinitions(EscalationEventDefinition.class);
  }

  public Set<ErrorEventDefinition> getErrorEventDefinitions() {
    return getDefinitions(ErrorEventDefinition.class);
  }

  private <T> Set<T> getDefinitions(Class<T> clazz) {
    return eventDefinitions.stream()
        .filter(clazz::isInstance)
        .map(clazz::cast)
        .collect(Collectors.toSet());
  }
}

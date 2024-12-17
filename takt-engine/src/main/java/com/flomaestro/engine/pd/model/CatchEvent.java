package com.flomaestro.engine.pd.model;

import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class CatchEvent extends Event
    implements WithIoMapping, WithEscalationEventDefinitions, WithErrorEventDefinitions {
  private Set<EventDefinition> eventDefinitions;

  public Optional<TimerEventDefinition> getTimerEventDefinition() {
    return getDefinition(TimerEventDefinition.class);
  }

  public Optional<MessageEventDefinition> getMessageventDefinition() {
    return getDefinition(MessageEventDefinition.class);
  }

  public Optional<LinkEventDefinition> getLinkventDefinition() {
    return getDefinition(LinkEventDefinition.class);
  }

  public Optional<TerminateEventDefinition> getTerminateEventDefinition() {
    return getDefinition(TerminateEventDefinition.class);
  }

  public Optional<EscalationEventDefinition> getEscalationEventDefinition() {
    return getDefinition(EscalationEventDefinition.class);
  }

  public Optional<ErrorEventDefinition> getErrorEventDefinition() {
    return getDefinition(ErrorEventDefinition.class);
  }

  private <T> Optional<T> getDefinition(Class<T> clazz) {
    return eventDefinitions.stream().filter(clazz::isInstance).map(clazz::cast).findFirst();
  }
}

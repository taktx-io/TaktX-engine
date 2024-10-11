package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class ThrowEvent extends Event implements WithEscalationEventDefinitions {
  private Set<EventDefinition> eventDefinitions;

  public Set<LinkEventDefinition> getLinkventDefinitions() {
    return eventDefinitions.stream()
        .filter(LinkEventDefinition.class::isInstance)
        .map(LinkEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }

  public Set<EscalationEventDefinition> getEscalationEventDefinitions() {
    return eventDefinitions.stream()
        .filter(EscalationEventDefinition.class::isInstance)
        .map(EscalationEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }
}

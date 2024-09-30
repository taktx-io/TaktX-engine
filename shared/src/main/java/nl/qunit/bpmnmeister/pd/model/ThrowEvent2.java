package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class ThrowEvent2 extends Event2 {
  private Set<EventDefinition2> eventDefinitions;

  public Set<LinkEventDefinition2> getLinkventDefinitions() {
    return eventDefinitions.stream()
        .filter(LinkEventDefinition2.class::isInstance)
        .map(LinkEventDefinition2.class::cast)
        .collect(Collectors.toSet());
  }
}

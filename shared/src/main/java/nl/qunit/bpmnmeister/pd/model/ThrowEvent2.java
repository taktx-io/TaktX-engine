package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class ThrowEvent2 extends Event2 {
  private Set<EventDefinitionDTO> eventDefinitions;
}

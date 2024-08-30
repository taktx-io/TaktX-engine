package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class CatchEvent2 extends Event2 implements WithIoMapping {
  private Set<EventDefinition2> eventDefinitions;
}

package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class EscalationEventDefinition extends EventDefinition {
  private String escalationRef;

  @Setter private EscalationEvent referencedEscalation;
}

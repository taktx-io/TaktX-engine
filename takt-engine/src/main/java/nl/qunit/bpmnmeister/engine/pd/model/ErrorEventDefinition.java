package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class ErrorEventDefinition extends EventDefinition {
  private String errorRef;

  @Setter private ErrorEvent referencedError;
}

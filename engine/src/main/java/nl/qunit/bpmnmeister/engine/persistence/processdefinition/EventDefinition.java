package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public class EventDefinition extends RootElement {
  protected EventDefinition(String id) {
    super(id);
  }
}

package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class LinkEventDefinition extends EventDefinition {

  private String name;
}

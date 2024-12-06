package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class IoVariableMapping {

  private final String source;
  private final String target;

  public IoVariableMapping(String source, String target) {
    this.source = source;
    this.target = target;
  }
}

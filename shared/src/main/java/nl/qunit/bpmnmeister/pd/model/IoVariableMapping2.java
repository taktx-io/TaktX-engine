package nl.qunit.bpmnmeister.pd.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class IoVariableMapping2 {

  private final String source;
  private final String target;

  public IoVariableMapping2(
      String source,
      String target) {
    this.source = source;
    this.target = target;
  }
}

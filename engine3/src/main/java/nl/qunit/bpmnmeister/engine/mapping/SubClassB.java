package nl.qunit.bpmnmeister.engine.mapping;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubClassB extends BaseClass {
  private String subNameB;

  public SubClassB() {}

  public SubClassB(String name, String subNameB) {
    super(name);
    this.subNameB = subNameB;
  }
}

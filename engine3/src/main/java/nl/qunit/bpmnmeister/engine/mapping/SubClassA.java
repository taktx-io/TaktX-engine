package nl.qunit.bpmnmeister.engine.mapping;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubClassA extends BaseClass {
  private String subNameA;

  public SubClassA() {}

  public SubClassA(String name, String subNameA) {
    super(name);
    this.subNameA = subNameA;
  }
}

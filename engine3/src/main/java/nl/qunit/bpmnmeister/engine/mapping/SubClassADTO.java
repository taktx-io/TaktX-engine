package nl.qunit.bpmnmeister.engine.mapping;

import lombok.Getter;

@Getter
public class SubClassADTO extends BaseClassDTO {
  private final String subNameA;

  public SubClassADTO(String name, String subNameA) {
    super(name);
    this.subNameA = subNameA;
  }
}

package nl.qunit.bpmnmeister.engine.mapping;

import lombok.Getter;

@Getter
public class SubClassBDTO extends BaseClassDTO {
  private final String subNameB;

  public SubClassBDTO(String name, String subNameB) {
    super(name);
    this.subNameB = subNameB;
  }
}

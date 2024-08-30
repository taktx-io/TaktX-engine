package nl.qunit.bpmnmeister.engine.mapping;

import lombok.Getter;

@Getter
public class BaseClassDTO {
  private final String name;

  public BaseClassDTO(String name) {
    this.name = name;
  }
}

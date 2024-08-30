package nl.qunit.bpmnmeister.engine.mapping;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseClass {
  private String name;

  BaseClass() {}

  protected BaseClass(String name) {
    this.name = name;
  }
}

package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public abstract class RootElement extends BaseElement {
  protected RootElement(String id) {
    super(id);
  }
}

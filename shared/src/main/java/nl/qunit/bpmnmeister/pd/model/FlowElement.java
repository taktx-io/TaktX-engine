package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public abstract class FlowElement extends BaseElement {
  protected FlowElement(String id) {
    super(id);
  }
}

package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public abstract class FlowElement extends BaseElement {
  protected FlowElement(@Nonnull BaseElementId id, @Nonnull BaseElementId parentId) {
    super(id, parentId);
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}

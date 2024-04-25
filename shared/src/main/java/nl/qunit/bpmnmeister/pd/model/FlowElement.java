package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public abstract class FlowElement extends BaseElement {
  protected FlowElement(@Nonnull String id, @Nonnull String parentId) {
    super(id);
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

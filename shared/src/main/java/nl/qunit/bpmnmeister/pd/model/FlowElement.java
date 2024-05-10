package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class FlowElement extends BaseElement {
  protected FlowElement(@Nonnull String id, @Nonnull String parentId) {
    super(id, parentId);
  }
}

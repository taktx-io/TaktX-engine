package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public abstract class RootElement extends BaseElement {
  protected RootElement(@Nonnull BaseElementId id, @Nonnull BaseElementId parentId) {
    super(id, parentId);
  }
}

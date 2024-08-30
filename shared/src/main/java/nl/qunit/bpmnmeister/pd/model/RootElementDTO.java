package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class RootElementDTO extends BaseElementDTO {
  protected RootElementDTO(@Nonnull String id, @Nonnull String parentId) {
    super(id, parentId);
  }
}

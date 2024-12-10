package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

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

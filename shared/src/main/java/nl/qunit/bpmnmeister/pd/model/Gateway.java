package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;

@Getter
public abstract class Gateway extends FlowNode {
  protected Gateway(
      @Nonnull BaseElementId id,
      @Nonnull BaseElementId parentId,
      @Nonnull Set<BaseElementId> incoming,
      @Nonnull Set<BaseElementId> outgoing) {
    super(id, parentId, incoming, outgoing);
  }
}

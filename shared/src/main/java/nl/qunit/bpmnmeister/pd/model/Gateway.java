package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;

@Getter
public abstract class Gateway extends FlowNode {
  protected Gateway(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing) {
    super(id, parentId, incoming, outgoing);
  }
}

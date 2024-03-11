package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;

@Getter
public abstract class FlowNode extends FlowElement {
  private final Set<BaseElementId> incoming;
  private final Set<BaseElementId> outgoing;

  protected FlowNode(
      @Nonnull BaseElementId id,
      @Nonnull BaseElementId parentId,
      @Nonnull Set<BaseElementId> incoming,
      @Nonnull Set<BaseElementId> outgoing) {
    super(id, parentId);
    this.incoming = incoming;
    this.outgoing = outgoing;
  }
}

package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class FlowNode<S extends FlowNodeState> extends FlowElement {
  private final Set<String> incoming;
  private final Set<String> outgoing;

  protected FlowNode(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing) {
    super(id, parentId);
    this.incoming = incoming;
    this.outgoing = outgoing;
  }

}

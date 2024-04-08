package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;

@Getter
public abstract class FlowNode extends FlowElement {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    FlowNode flowNode = (FlowNode) o;
    return Objects.equals(incoming, flowNode.incoming)
        && Objects.equals(outgoing, flowNode.outgoing);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), incoming, outgoing);
  }
}

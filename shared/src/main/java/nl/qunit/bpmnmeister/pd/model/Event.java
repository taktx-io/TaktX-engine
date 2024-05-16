package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@Getter
public abstract class Event<S extends FlowNodeState> extends FlowNode<S> {
  protected Event(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing) {
    super(id, parentId, incoming, outgoing);
  }
}

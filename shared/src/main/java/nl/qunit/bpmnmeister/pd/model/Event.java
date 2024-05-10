package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
public abstract class Event<S extends BpmnElementState> extends FlowNode<S> {
  protected Event(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing) {
    super(id, parentId, incoming, outgoing);
  }
}

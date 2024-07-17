package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class Event<S extends FlowNodeState> extends FlowNode<S> implements WithIoMapping {

  @Nonnull
  private final InputOutputMapping ioMapping;

  protected Event(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull InputOutputMapping ioMapping) {
    super(id, parentId, incoming, outgoing);
    this.ioMapping = ioMapping;
  }

  public abstract S getInitialState(String elementId, String inputFlowId, int passedCnt);

}

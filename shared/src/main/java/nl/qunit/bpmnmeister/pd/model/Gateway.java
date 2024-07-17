package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.GatewayState;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class Gateway<S extends GatewayState> extends FlowNode<S> {

  private final String defaultFlow;

  protected Gateway(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull String defaultFlow) {
    super(id, parentId, incoming, outgoing);
    this.defaultFlow = defaultFlow;
  }

  public abstract S getInitialState(String elementId, String inputFlowId, int passedCnt);

}

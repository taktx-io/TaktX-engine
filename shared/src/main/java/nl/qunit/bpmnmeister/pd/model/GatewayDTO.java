package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class GatewayDTO extends FlowNodeDTO {

  private final String defaultFlow;

  protected GatewayDTO(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull String defaultFlow) {
    super(id, parentId, incoming, outgoing);
    this.defaultFlow = defaultFlow;
  }
}

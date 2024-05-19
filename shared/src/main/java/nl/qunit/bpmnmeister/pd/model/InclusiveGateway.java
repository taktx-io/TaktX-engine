package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.InclusiveGatewayState;

@Getter
@EqualsAndHashCode(callSuper = true)
public class InclusiveGateway extends Gateway<InclusiveGatewayState> {

  @Nonnull
  private final String defaultFlow;

  @JsonCreator
  public InclusiveGateway(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("default") String defaultFlow) {
    super(id, parentId, incoming, outgoing, defaultFlow);
    this.defaultFlow = defaultFlow;
  }

  @Override
  public InclusiveGatewayState getInitialState(String inputFlowId, int passedCnt) {
    return new InclusiveGatewayState(UUID.randomUUID(), passedCnt, FlowNodeStateEnum.READY, inputFlowId, Set.of(), Set.of());
  }
}

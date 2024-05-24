package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ExclusiveGateway extends Gateway<ExclusiveGatewayState> {

  @JsonCreator
  public ExclusiveGateway(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("default") String defaultFlow) {
    super(id, parentId, incoming, outgoing, defaultFlow);
  }

  @Override
  public ExclusiveGatewayState getInitialState(String inputFlowId, int passedCnt) {
    return new ExclusiveGatewayState(
        UUID.randomUUID(), passedCnt, FlowNodeStateEnum.READY, inputFlowId);
  }
}

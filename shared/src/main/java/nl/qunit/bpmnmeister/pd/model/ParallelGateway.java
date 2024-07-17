package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.ParallelGatewayState;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ParallelGateway extends Gateway<ParallelGatewayState> {
  @JsonCreator
  public ParallelGateway(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing) {
    super(id, parentId, incoming, outgoing, Constants.NONE);
  }

  @Override
  public ParallelGatewayState getInitialState(String elementId, String inputFlowId, int passedCnt) {
    return new ParallelGatewayState(
        UUID.randomUUID(), elementId, Set.of(), passedCnt, FlowNodeStateEnum.READY, inputFlowId);
  }
}

package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.EndEventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
public class EndEvent extends ThrowEvent<EndEventState> {
  @JsonCreator
  public EndEvent(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing) {
    super(id, parentId, incoming, outgoing);
  }

  @Override
  public EndEventState getInitialState(String inputFlowId, int passedCnt) {
    return new EndEventState(UUID.randomUUID(), passedCnt, FlowNodeStateEnum.READY, inputFlowId);
  }
}

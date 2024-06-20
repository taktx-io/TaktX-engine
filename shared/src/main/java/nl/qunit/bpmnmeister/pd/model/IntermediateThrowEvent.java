package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.IntermediateThrowEventState;

@Getter
public class IntermediateThrowEvent extends ThrowEvent<IntermediateThrowEventState> {
  @JsonCreator
  public IntermediateThrowEvent(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("ioMapping") InputOutputMapping ioMapping,
      @Nonnull @JsonProperty("eventDefinitions") Set<EventDefinition> eventDefinitions) {
    super(id, parentId, incoming, outgoing, ioMapping, eventDefinitions);
  }

  @Override
  public IntermediateThrowEventState getInitialState(String inputFlowId, int passedCnt) {
    return new IntermediateThrowEventState(UUID.randomUUID(), passedCnt, FlowNodeStateEnum.READY, inputFlowId);
  }
}

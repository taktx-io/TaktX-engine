package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.BoundaryEventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
@EqualsAndHashCode(callSuper = true)
public class BoundaryEvent extends CatchEvent<BoundaryEventState> {

  private final String attachedToRef;
  private final boolean cancelActivity;

  @JsonCreator
  public BoundaryEvent(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("eventDefinitions") Set<EventDefinition> eventDefinitions,
      @Nonnull @JsonProperty("attachedToRef") String attachedToRef,
      @JsonProperty("cancelActivity") boolean cancelActivity) {
    super(id, parentId, incoming, outgoing, eventDefinitions);
    this.attachedToRef = attachedToRef;
    this.cancelActivity = cancelActivity;
  }

  @Override
  public BoundaryEventState getInitialState(String inputFlowId, int passedCnt) {
    return new BoundaryEventState(UUID.randomUUID(), passedCnt, FlowNodeStateEnum.READY, Set.of(),
        inputFlowId);
  }
}

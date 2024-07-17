package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@Getter
@SuperBuilder(toBuilder = true)
public class BoundaryEventState extends CatchEventState {

  private final UUID attachedInstanceId;
  private final Set<ScheduleKey> scheduleKeys;
  private final Set<MessageEventKey> messageEventKeys;

  @JsonCreator
  public BoundaryEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") FlowNodeStateEnum state,
      @Nonnull @JsonProperty("attachedInstanceId") UUID attachedInstanceId,
      @Nonnull @JsonProperty("scheduleKeys") Set<ScheduleKey> scheduleKeys,
      @Nonnull @JsonProperty("messageEventKeys") Set<MessageEventKey> messageEventKeys,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, state, inputFlowId);
    this.attachedInstanceId = attachedInstanceId;
    this.scheduleKeys = scheduleKeys;
    this.messageEventKeys = messageEventKeys;
  }
}

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

  private final Set<ScheduleKey> scheduleKeys;
  private final Set<MessageEventKey> messageEventKeys;

  @JsonCreator
  public BoundaryEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") FlowNodeStateEnum state,
      @Nonnull @JsonProperty("scheduleKeys") Set<ScheduleKey> scheduleKeys,
      @Nonnull @JsonProperty("messageEventKeys") Set<MessageEventKey> messageEventKeys,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId) {
    super(elementInstanceId, passedCnt, state, inputFlowId);
    this.scheduleKeys = scheduleKeys;
    this.messageEventKeys = messageEventKeys;
  }
}

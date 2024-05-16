package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@Getter
@ToString(callSuper = true)
public class IntermediateCatchEventState extends EventState {

  private final Set<ScheduleKey> scheduledKeys;

  @JsonCreator
  public IntermediateCatchEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") FlowNodeStateEnum flowNodeStateEnum,
      @Nonnull @JsonProperty("scheduledKeys") Set<ScheduleKey>scheduledKeys,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId
      ) {
    super(elementInstanceId, passedCnt, flowNodeStateEnum, inputFlowId);
    this.scheduledKeys = scheduledKeys;
  }

}

package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@Getter
@ToString(callSuper = true)
public class IntermediateCatchEventState extends EventState {

  private final IntermediateCatchEventStateEnum state;
  private final Set<ScheduleKey> scheduledKeys;

  @JsonCreator
  public IntermediateCatchEventState(
      @Nonnull @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") IntermediateCatchEventStateEnum state,
      @Nonnull @JsonProperty("scheduledKeys") Set<ScheduleKey>scheduledKeys) {
    super(elementInstanceId, passedCnt);
    this.state = state;
    this.scheduledKeys = scheduledKeys;
  }

}

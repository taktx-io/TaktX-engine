package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@Getter
public class BoundaryEventState extends EventState {

  @Nonnull
  private final BoundaryEventStateEnum state;
  private final Set<ScheduleKey> scheduleKeys;

  @JsonCreator
  public BoundaryEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") BoundaryEventStateEnum state,
      @Nonnull @JsonProperty("scheduleKeys") Set<ScheduleKey> scheduleKeys) {
    super(elementInstanceId, passedCnt);
    this.state = state;
    this.scheduleKeys = scheduleKeys;
  }

}

package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public class IntermediateCatchEventState extends CatchEventState {

  private final Set<ScheduleKey> scheduledKeys;

  @JsonCreator
  public IntermediateCatchEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("scheduledKeys") Set<ScheduleKey> scheduledKeys,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, inputFlowId);
    this.scheduledKeys = scheduledKeys;
  }
}

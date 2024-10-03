package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
public class BoundaryEventInstanceDTO extends CatchEventInstanceDTO {

  private final UUID attachedInstanceId;

  @JsonCreator
  public BoundaryEventInstanceDTO(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("attachedInstanceId") UUID attachedInstanceId,
      @Nonnull @JsonProperty("messageEventKeys") Map<MessageEventKey, Set<String>> messageEventKeys,
      @Nonnull @JsonProperty("state") CatchEventStateEnum state,
      @Nonnull @JsonProperty("scheduledKeys") Set<ScheduledKey> scheduledKeys) {
    super(elementInstanceId, elementId, passedCnt, state, scheduledKeys, messageEventKeys);
    this.attachedInstanceId = attachedInstanceId;
  }
}

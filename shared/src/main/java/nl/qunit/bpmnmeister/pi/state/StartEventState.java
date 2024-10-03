package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;

@Getter
@ToString(callSuper = true)
@SuperBuilder
public class StartEventState extends CatchEventState {
  @JsonCreator
  public StartEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") CatchEventStateEnum state,
      @Nonnull @JsonProperty("scheduledKeys") Set<ScheduledKey> scheduledKeys,
      @Nonnull @JsonProperty("messageEventKeys")
          Map<MessageEventKey, Set<String>> messageEventKeys) {
    super(elementInstanceId, elementId, passedCnt, state, scheduledKeys, messageEventKeys);
  }
}

package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.ScheduledKeyDTO;

@Getter
@ToString(callSuper = true)
@SuperBuilder
public class StartEventInstanceDTO extends CatchEventInstanceDTO {
  @JsonCreator
  public StartEventInstanceDTO(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") CatchEventStateEnum state,
      @Nonnull @JsonProperty("scheduledKeys") Set<ScheduledKeyDTO> scheduledKeys,
      @Nonnull @JsonProperty("messageEventKeys")
          Map<MessageEventKeyDTO, Set<String>> messageEventKeys) {
    super(elementInstanceId, elementId, passedCnt, state, scheduledKeys, messageEventKeys);
  }
}

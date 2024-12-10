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

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public class ReceiveTaskInstanceDTO extends TaskInstanceDTO {

  private String correlationKey;
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  @JsonCreator
  public ReceiveTaskInstanceDTO(
      @Nonnull @JsonProperty("state") ActtivityStateEnum state,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("correlationKey") String correlationKey,
      @JsonProperty("boundaryEventIds") Set<UUID> boundaryEventIds,
      @JsonProperty("messageEventKeys") Map<MessageEventKeyDTO, Set<String>> messageEventKeys) {
    super(state, elementInstanceId, elementId, passedCnt, loopCnt, boundaryEventIds);
    this.correlationKey = correlationKey;
    this.messageEventKeys = messageEventKeys;
  }
}

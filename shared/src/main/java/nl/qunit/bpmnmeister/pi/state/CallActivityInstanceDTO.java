package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder(toBuilder = true)
public class CallActivityInstanceDTO extends ActivityInstanceDTO {

  private final UUID childProcessInstanceId;

  @JsonCreator
  public CallActivityInstanceDTO(
      @Nonnull @JsonProperty("state") ActtivityStateEnum state,
      @Nonnull @JsonProperty("childProcessInstanceId") UUID childProcessInstanceId,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("boundaryEventIds") Set<UUID> boundaryEventIds) {
    super(state, elementId, elementInstanceId, passedCnt, loopCnt, boundaryEventIds);
    this.childProcessInstanceId = childProcessInstanceId;
  }
}

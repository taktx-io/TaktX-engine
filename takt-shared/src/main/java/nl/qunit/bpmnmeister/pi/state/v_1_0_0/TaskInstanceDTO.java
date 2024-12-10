package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public class TaskInstanceDTO extends ActivityInstanceDTO {
  @JsonCreator
  public TaskInstanceDTO(
      @JsonProperty("state") ActtivityStateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("boundaryEventIds") Set<UUID> boundaryEventIds) {
    super(state, elementId, elementInstanceId, passedCnt, loopCnt, boundaryEventIds);
  }
}

package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public class SendTaskInstanceDTO extends ExternalTaskInstanceDTO {

  @JsonCreator
  public SendTaskInstanceDTO(
      @Nonnull @JsonProperty("state") ActtivityStateEnum state,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("attempt") int attempt,
      @JsonProperty("boundaryEventIds") Set<UUID> boundaryEventIds) {
    super(state, elementInstanceId, elementId, passedCnt, loopCnt, boundaryEventIds, attempt);
  }
}

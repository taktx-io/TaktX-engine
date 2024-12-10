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
public class SubProcessInstanceDTO extends ActivityInstanceDTO implements WithFlowNodeInstancesDTO {

  private final FlowNodeInstancesDTO flowNodeInstances;

  @JsonCreator
  public SubProcessInstanceDTO(
      @Nonnull @JsonProperty("flowNodeInstances") FlowNodeInstancesDTO flowNodeInstances,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("state") ActtivityStateEnum state,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("boundaryEventIds") Set<UUID> boundaryEventIds) {
    super(state, elementId, elementInstanceId, passedCnt, loopCnt, boundaryEventIds);
    this.flowNodeInstances = flowNodeInstances;
  }
}

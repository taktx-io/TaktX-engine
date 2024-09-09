package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public class MultiInstanceState extends FlowNodeStateDTO {
  private List<FlowNodeStateDTO> instances;

  @JsonCreator
  public MultiInstanceState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") FlowNodeStateEnum state,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId,
      @Nonnull @JsonProperty("instances") List<FlowNodeStateDTO> instances) {
    super(elementInstanceId, elementId, passedCnt, state, inputFlowId);
    this.instances = instances;
  }
}

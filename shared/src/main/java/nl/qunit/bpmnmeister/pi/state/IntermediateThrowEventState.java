package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder
public class IntermediateThrowEventState extends ThrowEventState {
  @JsonCreator
  public IntermediateThrowEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("state") FlowNodeStateEnum flowNodeStateEnum,
      @JsonProperty("inputFlowId") String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, flowNodeStateEnum, inputFlowId);
  }
}

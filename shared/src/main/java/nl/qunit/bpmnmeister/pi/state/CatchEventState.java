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
@SuperBuilder(toBuilder = true)
public class CatchEventState extends EventState {
  @JsonCreator
  public CatchEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") FlowNodeStateEnum flowNodeStateEnum,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, flowNodeStateEnum, inputFlowId);
  }
}

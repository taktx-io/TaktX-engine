package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class StartEventState extends EventState {
  @JsonCreator
  public StartEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") FlowNodeStateEnum flowNodeStateEnum,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId) {
    super(elementInstanceId, passedCnt, flowNodeStateEnum, inputFlowId);
  }

}

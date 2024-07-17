package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
@SuperBuilder(toBuilder = true)
public class MultiInstanceState extends FlowNodeState {

  private final int loopCnt;

  @JsonCreator
  public MultiInstanceState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("elementId") String elementId,
      @JsonProperty("passedCnt") int passedCnt,
      @Nonnull @JsonProperty("state") FlowNodeStateEnum state,
      @Nonnull @JsonProperty("inputFlowId") String inputFlowId,
      @JsonProperty("loopCnt") int loopCnt) {
    super(elementInstanceId, elementId, passedCnt, state, inputFlowId);
    this.loopCnt = loopCnt;
  }
}

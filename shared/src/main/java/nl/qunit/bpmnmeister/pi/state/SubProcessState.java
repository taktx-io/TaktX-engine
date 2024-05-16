package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class SubProcessState extends ActivityState {

  @JsonCreator
  public SubProcessState(
      @Nonnull @JsonProperty("state") FlowNodeStateEnum state,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("inputFlowId") String inputFlowId) {
    super(state, elementInstanceId, passedCnt, loopCnt, inputFlowId);
  }

  @Override
  public ActivityState getNextLoopState() {
    return new SubProcessState(
        FlowNodeStateEnum.ACTIVE,
        this.getElementInstanceId(),
        this.getPassedCnt(),
        this.getLoopCnt() + 1,
        this.getInputFlowId());
  }

  @Override
  public ActivityState getFinishedLoopState() {
    return new SubProcessState(
        FlowNodeStateEnum.FINISHED,
        this.getElementInstanceId(),
        this.getPassedCnt() + 1,
        this.getLoopCnt() + 1,
        this.getInputFlowId());
  }

}

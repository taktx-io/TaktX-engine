  package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class SendTaskState extends TaskState {

  private final int attempt;

  @JsonCreator
  public SendTaskState(
      @Nonnull @JsonProperty("state") FlowNodeStateEnum state,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("attempt") int attempt,
      @JsonProperty("inputFlowId") String inputflowId) {
    super(state, elementInstanceId, passedCnt, loopCnt, inputflowId);
    this.attempt = attempt;
  }

  @Override
  public ActivityState getNextLoopState() {
    return new SendTaskState(
        FlowNodeStateEnum.ACTIVE,
        this.getElementInstanceId(),
        this.getPassedCnt(),
        this.getLoopCnt() + 1,
        this.getAttempt(),
        this.getInputFlowId());
  }

  @Override
  public ActivityState getFinishedLoopState() {
    return new SendTaskState(
        FlowNodeStateEnum.FINISHED,
        this.getElementInstanceId(),
        this.getPassedCnt() + 1,
        this.getLoopCnt() + 1,
        this.getAttempt(),
        this.getInputFlowId());
  }

}

package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class ServiceTaskState extends TaskState {

  private final int attempt;

  @JsonCreator
  public ServiceTaskState(
      @Nonnull @JsonProperty("state") ActivityStateEnum state,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("loopCnt") int loopCnt,
      @JsonProperty("attempt") int attempt) {
    super(state, elementInstanceId, passedCnt, loopCnt);
    this.attempt = attempt;
  }

  @Override
  public ActivityState getNextLoopState() {
    return new ServiceTaskState(
        ActivityStateEnum.ACTIVE,
        this.getElementInstanceId(),
        this.getPassedCnt(),
        this.getLoopCnt() + 1,
        this.getAttempt());
  }

  @Override
  public ActivityState getFinishedLoopState() {
    return new ServiceTaskState(
        ActivityStateEnum.FINISHED,
        this.getElementInstanceId(),
        this.getPassedCnt() + 1,
        this.getLoopCnt() + 1,
        this.getAttempt());
  }

}

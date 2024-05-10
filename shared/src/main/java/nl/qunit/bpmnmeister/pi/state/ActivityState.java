package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public abstract class ActivityState extends BpmnElementState {

  private final ActivityStateEnum state;
  private final int loopCnt;

  protected ActivityState(
      ActivityStateEnum state, UUID elementInstanceId, int passedCnt, int loopCnt) {
    super(elementInstanceId, passedCnt);
    this.state = state;
    this.loopCnt = loopCnt;
  }

  @JsonIgnore
  public abstract ActivityState getNextLoopState();

  @JsonIgnore
  public abstract ActivityState getFinishedLoopState();
}

package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class ActivityState extends BpmnElementState {

  private final ActivityStateEnum state;

  protected ActivityState(ActivityStateEnum state, UUID elementInstanceId, int passedCnt) {
    super(elementInstanceId, passedCnt);
    this.state = state;
  }
}

package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;

@Getter
public class MultiInstanceState extends BpmnElementState {

  private final int loopCnt;

  public MultiInstanceState(StateEnum state, UUID elementInstanceId, int loopCnt) {
    super(state, elementInstanceId);
    this.loopCnt = loopCnt;
  }
}

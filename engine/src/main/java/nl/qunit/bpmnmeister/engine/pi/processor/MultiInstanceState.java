package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;

@Getter
public class MultiInstanceState extends BpmnElementState {

  private final int loopCnt;

  protected MultiInstanceState(StateEnum state, UUID elementInstanceId, int loopCnt) {
    super(state, elementInstanceId);
    this.loopCnt = loopCnt;
  }
}

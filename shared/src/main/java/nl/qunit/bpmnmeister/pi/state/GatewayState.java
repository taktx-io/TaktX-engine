package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;

public abstract class GatewayState extends BpmnElementState {

  protected GatewayState(UUID elementInstanceId, int passedCnt) {
    super(elementInstanceId, passedCnt);
  }
}

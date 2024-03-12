package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;

public abstract class GatewayState extends BpmnElementState {

  protected GatewayState(StateEnum state, UUID elementInstanceId) {
    super(state, elementInstanceId);
  }
}

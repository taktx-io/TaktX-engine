package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class GatewayState extends BpmnElementState {

  private final ActivityStateEnum state;

  protected GatewayState(UUID elementInstanceId, int passedCnt, ActivityStateEnum state) {
    super(elementInstanceId, passedCnt);
    this.state = state;
  }


}

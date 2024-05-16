package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class GatewayState extends FlowNodeState {

  protected GatewayState(UUID elementInstanceId, int passedCnt, FlowNodeStateEnum state, String inputFlowId) {
    super(elementInstanceId, passedCnt, state, inputFlowId);
  }


}

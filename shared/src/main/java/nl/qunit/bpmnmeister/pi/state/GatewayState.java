package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class GatewayState extends FlowNodeState {

  protected GatewayState(
      UUID elementInstanceId, String elementId, int passedCnt, FlowNodeStateEnum state, String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, state, inputFlowId);
  }
}

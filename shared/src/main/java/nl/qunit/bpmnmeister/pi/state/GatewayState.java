package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public abstract class GatewayState extends FlowNodeStateDTO {

  protected GatewayState(
      UUID elementInstanceId, String elementId, int passedCnt, String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, inputFlowId);
  }
}

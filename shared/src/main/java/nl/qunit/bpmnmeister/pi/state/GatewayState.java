package nl.qunit.bpmnmeister.pi.state;

import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
public abstract class GatewayState extends FlowNodeStateDTO {
  private Set<String> selectedOutputFlows;

  protected GatewayState(
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      String inputFlowId,
      Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt, inputFlowId);
    this.selectedOutputFlows = selectedOutputFlows;
  }
}

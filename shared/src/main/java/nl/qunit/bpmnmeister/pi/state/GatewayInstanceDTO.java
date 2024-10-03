package nl.qunit.bpmnmeister.pi.state;

import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
public abstract class GatewayInstanceDTO extends FlowNodeInstanceDTO {
  private Set<String> selectedOutputFlows;

  protected GatewayInstanceDTO(
      UUID elementInstanceId, String elementId, int passedCnt, Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt);
    this.selectedOutputFlows = selectedOutputFlows;
  }
}

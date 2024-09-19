package nl.qunit.bpmnmeister.pi.instances;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.InclusiveGateway2;

@Getter
@Setter
public class InclusiveGatewayInstance extends GatewayInstance<InclusiveGateway2> {
  private Set<String> triggeredInputFlows;
  private Set<String> selectedOutputFlows;

  public InclusiveGatewayInstance(FLowNodeInstance<?> parentInstance, InclusiveGateway2 flowNode) {
    super(parentInstance, flowNode);
  }
}

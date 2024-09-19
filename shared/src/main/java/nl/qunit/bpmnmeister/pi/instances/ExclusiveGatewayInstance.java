package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway2;

@NoArgsConstructor
public class ExclusiveGatewayInstance extends GatewayInstance<ExclusiveGateway2> {

  public ExclusiveGatewayInstance(FLowNodeInstance parentInstance, ExclusiveGateway2 flowNode) {
    super(parentInstance, flowNode);
  }
}

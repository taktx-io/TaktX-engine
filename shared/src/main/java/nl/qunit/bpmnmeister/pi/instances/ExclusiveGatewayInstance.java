package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;

@NoArgsConstructor
public class ExclusiveGatewayInstance extends GatewayInstance<ExclusiveGateway> {

  public ExclusiveGatewayInstance(FLowNodeInstance<?> parentInstance, ExclusiveGateway flowNode) {
    super(parentInstance, flowNode);
  }
}

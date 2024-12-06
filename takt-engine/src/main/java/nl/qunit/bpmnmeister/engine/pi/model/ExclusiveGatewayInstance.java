package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.ExclusiveGateway;

@NoArgsConstructor
public class ExclusiveGatewayInstance extends GatewayInstance<ExclusiveGateway> {

  public ExclusiveGatewayInstance(FLowNodeInstance<?> parentInstance, ExclusiveGateway flowNode) {
    super(parentInstance, flowNode);
  }

  @Override
  public void resetFlows() {}
}

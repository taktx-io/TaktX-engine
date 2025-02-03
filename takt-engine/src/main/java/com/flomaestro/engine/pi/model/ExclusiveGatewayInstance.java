package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ExclusiveGateway;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ExclusiveGatewayInstance extends GatewayInstance<ExclusiveGateway> {

  public ExclusiveGatewayInstance(
      FlowNodeInstance<?> parentInstance, ExclusiveGateway flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public void resetFlows() {}
}

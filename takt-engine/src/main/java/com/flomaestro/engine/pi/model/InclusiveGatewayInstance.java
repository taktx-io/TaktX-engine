package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.InclusiveGateway;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InclusiveGatewayInstance extends GatewayInstance<InclusiveGateway> {
  private Set<String> triggeredInputFlows = new HashSet<>();

  public InclusiveGatewayInstance(FlowNodeInstance<?> parentInstance, InclusiveGateway flowNode) {
    super(parentInstance, flowNode);
  }

  @Override
  public void resetFlows() {
    this.triggeredInputFlows.clear();
  }

  public void addTriggeredInputFlow(String inputFlowId) {
    this.triggeredInputFlows.add(inputFlowId);
  }
}

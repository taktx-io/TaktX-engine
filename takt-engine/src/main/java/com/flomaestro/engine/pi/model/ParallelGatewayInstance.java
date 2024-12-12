package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ParallelGateway;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Getter
public class ParallelGatewayInstance extends GatewayInstance<ParallelGateway> {
  private final Set<String> triggeredInputFlows = new HashSet<>();

  public ParallelGatewayInstance(FlowNodeInstance<?> parentInstance, ParallelGateway flowNode) {
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

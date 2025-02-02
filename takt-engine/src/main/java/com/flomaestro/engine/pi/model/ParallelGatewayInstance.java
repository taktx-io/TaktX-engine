package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ParallelGateway;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Getter
public class ParallelGatewayInstance extends GatewayInstance<ParallelGateway> {
  private final Set<String> triggeredFlows = new HashSet<>();

  public ParallelGatewayInstance(FlowNodeInstance<?> parentInstance, ParallelGateway flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public void resetFlows() {
    this.triggeredFlows.clear();
  }

  public void addTriggeredFlow(String inputFlowId) {
    this.triggeredFlows.add(inputFlowId);
  }
}

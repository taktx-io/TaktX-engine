package nl.qunit.bpmnmeister.pi.instances;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;

@Getter
public class ParallelGatewayInstance extends GatewayInstance<ParallelGateway> {
  private Set<String> triggeredInputFlows = new HashSet<>();

  public ParallelGatewayInstance(FLowNodeInstance<?> parentInstance, ParallelGateway flowNode) {
    super(parentInstance, flowNode);
  }

  @Override
  public void resetFlows() {
    this.triggeredInputFlows.clear();
  }

  public void setTriggeredInputFlows(Set<String> triggeredInputFlows) {
    this.triggeredInputFlows = new HashSet<>(triggeredInputFlows);
  }

  public void addTriggeredInputFlow(String inputFlowId) {
    this.triggeredInputFlows.add(inputFlowId);
  }

  public void clearTriggeredInputFlows() {
    this.triggeredInputFlows.clear();
  }
}

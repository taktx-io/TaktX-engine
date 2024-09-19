package nl.qunit.bpmnmeister.pi.instances;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway2;

@Getter
public class ParallelGatewayInstance extends GatewayInstance<ParallelGateway2> {
  private Set<String> triggeredFlows = new HashSet<>();

  public ParallelGatewayInstance(FLowNodeInstance<?> parentInstance, ParallelGateway2 flowNode) {
    super(parentInstance, flowNode);
  }

  public void setTriggeredFlows(Set<String> triggeredFlows) {
    this.triggeredFlows = new HashSet<>(triggeredFlows);
  }

  public void addTriggeredFlow(String inputFlowId) {
    this.triggeredFlows.add(inputFlowId);
  }

  public void clearTriggeredFlows() {
    this.triggeredFlows.clear();
  }
}

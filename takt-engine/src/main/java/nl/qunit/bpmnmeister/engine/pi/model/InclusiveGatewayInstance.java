package nl.qunit.bpmnmeister.engine.pi.model;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pd.model.InclusiveGateway;

@Getter
@Setter
public class InclusiveGatewayInstance extends GatewayInstance<InclusiveGateway> {
  private Set<String> triggeredInputFlows = new HashSet<>();

  public InclusiveGatewayInstance(FLowNodeInstance<?> parentInstance, InclusiveGateway flowNode) {
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
